# Build script - sets JAVA_HOME and runs Gradle
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$PSScriptRoot\gradlew.bat" @args
