"""Compare compiled Hook annotations/bindings and Bridge interface with a baseline JAR."""
from pathlib import Path
import argparse
import io
import struct
import zipfile


class ClassFile:
    def __init__(self, payload):
        self.stream = io.BytesIO(payload)
        if self.u4() != 0xCAFEBABE:
            raise ValueError('Invalid Java class')
        self.u2()
        self.u2()
        self.pool = [None] * self.u2()
        at = 1
        while at < len(self.pool):
            tag = self.u1()
            if tag == 1:
                self.pool[at] = self.stream.read(self.u2()).decode('utf-8', errors='replace')
            elif tag in (3, 4):
                self.pool[at] = (tag, self.stream.read(4))
            elif tag in (5, 6):
                self.pool[at] = (tag, self.stream.read(8))
                at += 1
            elif tag in (7, 8, 16, 19, 20):
                self.u2()
            elif tag in (9, 10, 11, 12, 17, 18):
                self.u4()
            elif tag == 15:
                self.u1()
                self.u2()
            else:
                raise ValueError('Unsupported constant pool tag: ' + str(tag))
            at += 1
        self.u2()
        self.u2()
        self.u2()
        self.stream.read(self.u2() * 2)
        for _ in range(self.u2()):
            self.member()
        self.methods = [self.member() for _ in range(self.u2())]
        self.attributes = self.attributes_read()

    def u1(self):
        return struct.unpack('>B', self.stream.read(1))[0]

    def u2(self):
        return struct.unpack('>H', self.stream.read(2))[0]

    def u4(self):
        return struct.unpack('>I', self.stream.read(4))[0]

    def member(self):
        access, name, descriptor = self.u2(), self.u2(), self.u2()
        return (access, self.pool[name], self.pool[descriptor], self.attributes_read())

    def annotation(self):
        kind = self.pool[self.u2()]
        pairs = [(self.pool[self.u2()], self.value()) for _ in range(self.u2())]
        return (kind, sorted(pairs))

    def value(self):
        tag = chr(self.u1())
        if tag in 'BCDFIJSZsc':
            return (tag, self.pool[self.u2()])
        if tag == 'e':
            return (tag, self.pool[self.u2()], self.pool[self.u2()])
        if tag == '@':
            return (tag, self.annotation())
        if tag == '[':
            return (tag, [self.value() for _ in range(self.u2())])
        raise ValueError('Unsupported annotation value: ' + tag)

    def attributes_read(self):
        result = {}
        for _ in range(self.u2()):
            name = self.pool[self.u2()]
            payload = self.stream.read(self.u4())
            original = self.stream
            self.stream = io.BytesIO(payload)
            try:
                if name in ('RuntimeVisibleAnnotations', 'RuntimeInvisibleAnnotations'):
                    result[name] = [self.annotation() for _ in range(self.u2())]
                elif name in ('RuntimeVisibleParameterAnnotations', 'RuntimeInvisibleParameterAnnotations'):
                    result[name] = [[self.annotation() for _ in range(self.u2())]
                                    for _ in range(self.u1())]
            finally:
                self.stream = original
        return result


def inspect(path):
    patches = {}
    with zipfile.ZipFile(path) as jar:
        bridge = ClassFile(jar.read('oldpoint/viewpoint/Bridge.class'))
        # Include package-level call sites as well as Lua-visible public methods.
        interface = sorted((access, name, descriptor) for access, name, descriptor, _ in bridge.methods
                           if not access & 0x0002 and name != '<clinit>')
        for name in jar.namelist():
            if not name.endswith('.class'):
                continue
            payload = jar.read(name)
            if b'Lme/zed_0xff/zombie_buddy/Patch;' not in payload:
                continue
            cls = ClassFile(payload)
            annotations = cls.attributes.get('RuntimeVisibleAnnotations', [])
            if not any(kind == 'Lme/zed_0xff/zombie_buddy/Patch;' for kind, _ in annotations):
                continue
            patches[name] = (cls.attributes, cls.methods)
        if any('CameraControllerTest' in name for name in jar.namelist()):
            raise AssertionError('Test classes leaked into distribution JAR')
    return interface, bridge.attributes, patches


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--current', type=Path, required=True)
    args = parser.parse_args()
    before, after = inspect(args.baseline), inspect(args.current)
    if before[0] != after[0]:
        raise AssertionError('Bridge public/package interface changed')
    if before[1] != after[1]:
        raise AssertionError('Bridge Lua exposure changed')
    if before[2] != after[2]:
        changed = sorted(name for name in before[2].keys() | after[2].keys()
                         if before[2].get(name) != after[2].get(name))
        raise AssertionError('Hook contracts changed: ' + ', '.join(changed))
    print(f'PASS {len(after[0])} Bridge public/package methods and Lua exposure unchanged')
    print(f'PASS {len(after[2])} Hook classes: targets, phases and parameter bindings unchanged')


if __name__ == '__main__':
    main()
