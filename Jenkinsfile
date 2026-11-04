@Library('wf-ci') _

def collect = '''
mkdir -p dist
find versions/*/build/libs -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' -exec cp {} dist/ \\;
'''

wfBuild(
    repo: 'Golem-Core',
    jdk: '25',
    ci: false,
    branches: ['main'],
    build: 'chiseledBuild --no-daemon --stacktrace',
    archive: 'versions/*/build/libs/*.jar',
    prerelease: [versionKey: 'mod.version', name: 'Golem Core', collect: collect],
    modRelease: [
        versionCmd: "grep -E '^mod.version=' gradle.properties | cut -d= -f2- | tr -d '[:space:]'",
        build: 'chiseledBuild --no-daemon --stacktrace',
        publish: 'publishAllMods --no-daemon --stacktrace',
        name: 'Golem Core @VERSION@',
        collect: '''
mkdir -p dist
find versions/*/build/release -maxdepth 1 -name '*.jar' -exec cp {} dist/ \\;
for jar in versions/*/build/release/*.jar; do
  cp "${jar%/release/*}/libs/${jar##*/}" "dist/$(basename "$jar" .jar)-dev.jar"
done
(cd harness && bash ./gradlew -q jar --no-daemon) && cp harness/build/libs/golem-harness.jar dist/
ls -l dist
''',
    ],
)
