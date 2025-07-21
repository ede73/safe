#git clone /mnt/c/users/ede/src/safe
#cd safe
[[  $(basename "$(pwd)") != "safe" ]] && {
  echo "Need to run this on base safe folder!"
  exit 10
}
cat <<EOF
# Remember to add something along these lines to .bashrc(or your fav) (and relogin or bash -)
# Android SDK
ANDROID_HOME=/opt/android-sdk
export ANDROID_HOME
PATH=\${PATH}:\${ANDROID_HOME}/tools:\${ANDROID_HOME}/cmdline-tools/latest/bin
EOF

cp /mnt/c/users/ede/src/safe/app/google-services.json app
sed "s,^sdk.dir.*,sdk.dir=${ANDROID_HOME},g" /mnt/c/users/ede/src/safe/local.properties >local.properties
# sid sdk.dir to point to $ANDROID_HOME

cat << EOF
# Also if you dont yet have android SDK nor java
# AS ROOT
apt-get update && apt-get install -y --no-install-recommends curl unzip lib32stdc++6 lib32z1 libx11-6 aptinstall openjdk-21-jdk
mkdir -p \${ANDROID_HOME}/cmdline-tools/latest && cd \${ANDROID_HOME}/cmdline-tools/latest \
    && curl -o sdk.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
    && unzip sdk.zip && rm sdk.zip && mv cmdline-tools/* . && rmdir cmdline-tools
yes | sdkmanager --licenses
sdkmanager "build-tools;${SDK_MAJOR}.0.0" "platforms;android-${SDK_MAJOR}"
sdkmanager "emulator" "system-images;android-${SDK_MAJOR};default;x86_64"
sdkmanager "platform-tools"

# AS YOU
#RUN mkdir -p $HOME/.android/avd/Pixel_2_API_33.avd
#co config.ini $HOME/.android/avd/Pixel_2_API_33.avd/config.ini
RUN echo "no" | avdmanager create avd -n Pixel_2_API_33.avd -k "system-images;android-34;default;x86_64"
\${ANDROID_HOME}/emulator/emulator -avd Pixel_2_API_33.avd -no-audio -no-window

# If emulator doesnt start, enable nestedVirtualization for wsl2
# Add your self to kvm group
EOF
