package oldpoint.viewpoint;

/** Preserve the single compatibility-error report shared by camera and drawing. */
final class CompatibilityErrors {
    private static boolean reported;

    private CompatibilityErrors() {}

    static void report(Throwable error) {
        if (!reported) {
            reported = true;
            System.out.println("[OldPoint] compatibility error: " + error);
            error.printStackTrace(System.out);
        }
    }
}
