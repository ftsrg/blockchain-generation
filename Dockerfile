# Build Refinery and the measurement environment
FROM public.ecr.aws/amazoncorretto/amazoncorretto:21-al2023-jdk AS builder
RUN dnf install -y findutils && \
    dnf clean all
COPY --link refinery/ /app/refinery/
RUN cd /app/refinery && \
    ./gradlew :refinery-language-web:distTar --no-daemon
COPY --link build.gradle gradlew settings.gradle /app/
COPY --link gradle/ /app/gradle/
COPY --link src/ /app/src/
RUN cd /app && \
    ./gradlew distTar --no-daemon

# Unpack Refinery and remove unneeded architecture-specific libraries
# to reduce image size
FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal AS unpack
RUN dnf install -y sed tar && \
    dnf clean all
COPY --link --from=builder /app/refinery/subprojects/language-web/build/distributions/refinery-language-web-0.0.0-SNAPSHOT.tar /app//build/distributions/refinery-blockchain-0.0.0-SNAPSHOT.tar /app/
RUN mkdir /app/refinery && \
    cd /app/refinery && \
    tar -xvf ../refinery-language-web-0.0.0-SNAPSHOT.tar --strip-components=1 && \
    tar -xvf ../refinery-blockchain-0.0.0-SNAPSHOT.tar --strip-components=1 && \
    rm lib/ortools-{darwin,win32}-*.jar && \
    if [ "x$(uname -m)" = "xaarch64" ]; then \
        rm lib/ortools-linux-x86-64-*.jar && \
        sed -i'' 's/:\$APP_HOME\/lib\/ortools-\(darwin\|win32\|linux-x86-64\)[^:]\+\.jar//g' bin/refinery-language-web && \
        sed -i'' 's/:\$APP_HOME\/lib\/ortools-\(darwin\|win32\|linux-x86-64\)[^:]\+\.jar//g' bin/refinery-blockchain; \
    else \
        rm lib/ortools-linux-aarch64-*.jar && \
        sed -i'' 's/:\$APP_HOME\/lib\/ortools-\(darwin\|win32\|linux-aarch64\)[^:]\+\.jar//g' bin/refinery-language-web && \
        sed -i'' 's/:\$APP_HOME\/lib\/ortools-\(darwin\|win32\|linux-aarch64\)[^:]\+\.jar//g' bin/refinery-blockchain; \
    fi

# Create and optimized JRE with the required system libraries
FROM public.ecr.aws/amazoncorretto/amazoncorretto:21-al2023-jdk AS jlink
RUN dnf install -y binutils && \
    dnf clean all
RUN jlink --no-header-files --no-man-pages \
    --module-path=/crossjdk/jmods --strip-debug --add-modules \
    java.base,java.logging,java.xml,jdk.zipfs \
    --output /jlink

# Final measurement environment
FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal
COPY --link --from=jlink /jlink /usr/lib/java
ENV JAVA_HOME="/usr/lib/java" PATH="/usr/lib/java/bin:${PATH}"
RUN dnf install -y findutils shadow-utils && \
    dnf clean all
RUN useradd -m refinery && \
    mkdir /app && \
    chown 1000:1000 /app
USER 1000
WORKDIR /app
RUN curl -L -O "https://github.com/conda-forge/miniforge/releases/latest/download/Miniforge3-$(uname)-$(uname -m).sh" && \
    bash Miniforge3-$(uname)-$(uname -m).sh -b -p "${HOME}/conda" && \
    rm Miniforge3-$(uname)-$(uname -m).sh
COPY --link --chown=1000:1000 environment.yml /app/
RUN bash -c "source ${HOME}/conda/etc/profile.d/conda.sh && \
    conda env create -f environment.yml"
RUN echo 'source ${HOME}/conda/etc/profile.d/conda.sh' >> ${HOME}/.bash_profile && \
    echo 'conda activate refinery-blockchain' >> ${HOME}/.bash_profile
COPY --link --chown=1000:1000 jupyter_lab_config.py /home/refinery/.jupyter/
COPY --link --chown=1000:1000 *.ipynb *.sh LICENSE models24-refinery-blockchain.pdf README REQUIREMENTS STATUS /app/
COPY --link --chown=1000:1000 ASP/ /app/ASP/
COPY --link --chown=1000:1000 output/ /app/output/
COPY --link --chown=1000:1000 problemDescriptions/ /app/problemDescriptions/
COPY --link --chown=1000:1000 --from=unpack /app/refinery/ /app/refinery/
ENV REFINERY_LISTEN_HOST=0.0.0.0 REFINERY_LISTEN_PORT=8889 REFINERY_SEMANTICS_TIMEOUT_MS=5000 REFINERY_SEMANTICS_WARMUP_TIMEOUT_MS=5000 REFINERY_MODEL_GENERATION_TIMEOUT_SEC=300 REFINERY_LIBRARY_PATH=/app/problemDescriptions:/app/output/Refinery
EXPOSE 8888 8889
ENTRYPOINT ["/bin/bash", "-l"]
