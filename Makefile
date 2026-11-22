run-guide:
	cd guide && mdbook serve --hostname 127.0.0.1

check: check-unit

check-unit:
	JAVA_HOME=${HOME}/Android/android-studio/jbr ./gradlew :app:testFossDebugUnitTest --console=plain --rerun-tasks
