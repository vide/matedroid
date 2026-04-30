# Image for `bundle exec fastlane screengrab` so the host laptop doesn't need
# Ruby/bundler/fastlane installed.
#
# Build context is the repo root so the Gemfile (at the project root) is
# copied in. Build:
#   docker build -t matedroid-screengrab -f docker/screengrab/Dockerfile .
# Run (Linux):
#   docker run --rm --network host \
#     --user "$(id -u):$(id -g)" \
#     -v "$PWD":/workspace \
#     matedroid-screengrab
#
# `--network host` is what lets the container's adb client reach the host's
# adb-server at 127.0.0.1:5037, so the device the host is already paired with
# (e.g. via ADB-over-WiFi) shows up inside the container too.

FROM ruby:3-slim

ENV DEBIAN_FRONTEND=noninteractive

# - android-tools-adb: the `adb` binary fastlane shells out to
# - build-essential: needed for native gem extensions during `bundle install`
# - ca-certificates: HTTPS for rubygems.org
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        android-tools-adb \
        build-essential \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Cache the gem-install layer separately from the project mount: as long as
# the Gemfile doesn't change, this layer is reused.
COPY Gemfile ./
RUN bundle install

# The image installs gems as root, but we run the container with --user
# matching the host UID so written files have the right ownership. Make the
# bundle path readable for any uid, and give bundler/fastlane a writable HOME.
RUN chmod -R a+rx /usr/local/bundle && \
    mkdir -p /tmp/home && chmod 777 /tmp/home
ENV HOME=/tmp/home

ENTRYPOINT ["bundle", "exec", "fastlane"]
CMD ["screengrab"]
