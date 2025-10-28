# SAGIT Demo: Command-Line Flow

This section gives you a **zero-conf, live demo** to showcase **SAGIT** in front of judges: install into a fresh repo, auto-draft commit messages, generate semantic diffs, record metadata, summarize changes, list impacted tests, handle renames/add/delete, export data, and verify setup — all **locally** with a single JAR.

---

## ✅ Prerequisites

* **Java 17+** (`java -version`)
* **Git** (`git --version`)
* The SAGIT JAR built locally (via Maven):

  ```bash
  # from the SAGIT project root
  mvn -q -DskipTests package
  # this typically produces target/sagit-0.1.0[-shaded].jar
  ```

> If your build produces `sagit-0.1.0-shaded.jar`, great. If not, use the non-shaded `sagit-0.1.0.jar`.

---

## 🎬 One-Command Demo Script

> Copy the block below into your terminal on macOS/Linux. It will **create /tmp/sagit-demo**, install SAGIT, and walk through every feature.

```bash
#!/usr/bin/env bash
set -euo pipefail

# ===== 0) Pre-flight: locate the JAR (auto-detect shaded first, then fallback) =====
JAR="$(ls -1 target/sagit-*-shaded.jar 2>/dev/null || true)"
if [[ -z "${JAR}" ]]; then
  JAR="$(ls -1 target/sagit-*.jar 2>/dev/null | head -n1 || true)"
fi
if [[ -z "${JAR}" || ! -f "${JAR}" ]]; then
  echo "❌ SAGIT jar not found in ./target. Build it first:"
  echo "   mvn -q -DskipTests package"
  exit 1
fi

echo "Using JAR: ${JAR}"
java -version
git --version

# ===== 1) Create a clean demo repo =====
rm -rf /tmp/sagit-demo && mkdir -p /tmp/sagit-demo && cd /tmp/sagit-demo
git init

# ===== 2) Install SAGIT into this repo =====
java -jar "${JAR}" setup

echo "---- Installed hooks ----"
ls -l .git/hooks | sed -n '1,200p'
echo "---- Runtime JAR ----"
ls -l .sagit/sagit.jar

# ===== 3) Seed tests mapping + config (for 'impacted' demo) =====
mkdir -p .sagit
cat > .sagit/tests.map <<'MAP'
^src/main/java/(.*)\.java$ => src/test/java/$1Test.java
MAP

cat > .sagit/config.json <<'JSON'
{
  "commitTemplate": null,
  "languages": ["java"],
  "impactedRules": ".sagit/tests.map"
}
JSON

# ===== 4) First commit (first-commit safe, drafted message) =====
mkdir -p src/main/java/demo src/test/java/demo
cat > src/main/java/demo/Hello.java <<'JAVA'
package demo;
public class Hello {
  public void ping() {}
}
JAVA

git add -A
echo
echo ">>> An editor will open with a drafted header (e.g., feat(demo): add/update demo)"
echo ">>> Just save & close the editor to continue."
git commit   # save & close the drafted message

echo "---- meta last ----"
java -jar .sagit/sagit.jar meta last || true

echo "---- hook log (tail) ----"
tail -n 20 .sagit/hook.log || true

# ===== 5) Stage a change and preview semantic diff BEFORE committing =====
echo "// tweak 1" >> src/main/java/demo/Hello.java
git add -A

echo "---- diff --semantic (staged vs HEAD) ----"
java -jar .sagit/sagit.jar diff --semantic

# ===== 6) Commit the staged change, then summarize since previous =====
git commit   # save & close the drafted message
echo "---- describe (markdown) since HEAD~1 ----"
java -jar .sagit/sagit.jar describe --since HEAD~1

echo "---- describe (json) since HEAD~1 ----"
java -jar .sagit/sagit.jar describe --since HEAD~1 --format json

# ===== 7) Impacted tests (uses tests.map rule) =====
cat > src/test/java/demo/HelloTest.java <<'JAVA'
package demo;
public class HelloTest { public void testPing() {} }
JAVA
git add -A
git commit -m "test(demo): add HelloTest skeleton"

echo "---- impacted tests since HEAD~1 ----"
java -jar .sagit/sagit.jar impacted --since HEAD~1

echo "---- impacted tests (only existing files) ----"
java -jar .sagit/sagit.jar impacted --since HEAD~1 --only-changed-tests

# ===== 8) Demonstrate rename/copy handling =====
git mv src/main/java/demo/Hello.java src/main/java/demo/Greeting.java
git add -A
git commit   # save & close drafted message
echo "---- describe after rename ----"
java -jar .sagit/sagit.jar describe --since HEAD~1

# ===== 9) Demonstrate add & delete in one commit =====
echo "public class Foo {}" > src/main/java/demo/Foo.java
git rm src/test/java/demo/HelloTest.java
git add -A
git commit   # save & close drafted message
echo "---- describe after add+delete ----"
java -jar .sagit/sagit.jar describe --since HEAD~1

# ===== 10) Export metadata for CI/Dashboards =====
echo "---- export metadata to CSV & JSONL ----"
java -jar .sagit/sagit.jar meta --export csv > sagit_meta.csv
cp .sagit/meta.jsonl sagit_meta.jsonl
ls -lh sagit_meta.csv sagit_meta.jsonl

# ===== 11) Health check =====
echo "---- verify ----"
java -jar .sagit/sagit.jar verify

# ===== 12) Summary =====
echo
echo "✅ Demo complete."
echo "You showed: setup → drafted commit → semantic diff → describe (MD+JSON) → impacted tests → rename → add/delete → export → verify"
```

---

## 🧭 What This Demonstrates (Talking Points)

1. **Instant setup**: `sagit setup` installs hooks and a local runtime in `.sagit/`.
2. **Commit hygiene**: drafted **Conventional Commit** headers via `prepare-commit-msg`.
3. **Semantic diffs**: Java type/method deltas and file stats with `diff --semantic`.
4. **Durable metadata**: Append-only `.sagit/meta.jsonl` per commit (+ CSV export).
5. **Change summaries**: `describe --since HEAD~1` in **Markdown** and **JSON**.
6. **Impacted tests**: rules-based mapping with `--only-changed-tests` filter.
7. **Edge cases handled**: **first commit**, **rename/copy**, **add/delete**.
8. **Local-first**: no servers, no keys, fully Git-compatible.
9. **Verification**: `verify` confirms hooks/JAR/config presence.

---

## 🧰 Troubleshooting (Quick)

* **Editor blocks commit**: just save & close (the draft is pre-filled).
* **No JAR found**: ensure `mvn -q -DskipTests package` was run; check `target/` path.
* **Windows**: run equivalent steps in **Git Bash** or adapt commands for PowerShell (hooks install works cross-platform; demo script above is bash).

---

## 🧹 Cleanup

```bash
rm -rf /tmp/sagit-demo
```

> Keep the `sagit_meta.csv` / `sagit_meta.jsonl` artifacts if you want to show CI/dashboard integration.
