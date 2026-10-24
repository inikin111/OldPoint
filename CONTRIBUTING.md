# Contributing to OldPoint

请以最新的 `master` 为基础创建自己的修改分支，并向 `master` 提交 Pull Request。`master` 上的改动需要仓库所有者 `@lxf-sinksky` 审查并批准；不要直接向 `master` 推送。

```powershell
git switch master
git pull --ff-only origin master
git switch -c feature/your-change
```

修改完成后，将自己的分支推送到 fork 或获授权的仓库，创建以 `master` 为目标的 Pull Request。后续新增提交会使旧的批准失效，需要重新审查。

Start your branch from the latest `master`, push it to a repository you can access, and open a pull request targeting `master`. The repository owner must approve the changes before they are merged. Do not push directly to `master`.
