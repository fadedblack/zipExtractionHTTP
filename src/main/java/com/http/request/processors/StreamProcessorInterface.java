package com.http.request.processors;

import java.io.IOException;

@FunctionalInterface
public interface StreamProcessorInterface {
    void processStream(byte[] inputStream, String name) throws IOException;
}
