package com.nimrod.output;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nimrod.cli.DecodeCommand.Format;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/**
 * Serialises decoded row data as JSON and writes to stdout or a file.
 * Supports pretty, compact, and NDJSON output formats.
 */
@Component
public class JsonWriter {

    private static final Logger LOG = LoggerFactory.getLogger(JsonWriter.class);

    private final ObjectMapper prettyMapper;
    private final ObjectMapper compactMapper;

    public JsonWriter() {
        this.prettyMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.compactMapper = new ObjectMapper();
    }

    /**
     * Write the decoded rows as JSON.
     *
     * @param rows   list of decoded row maps
     * @param format output format (pretty, compact, ndjson)
     * @param output output file, or null for stdout
     */
    public void write(List<Map<String, Object>> rows, Format format, File output)
            throws IOException {

        try (OutputStream os = output != null
                ? new FileOutputStream(output)
                : new NonClosingOutputStream(System.out);
             PrintStream ps = new PrintStream(os)) {

            switch (format) {
                case pretty -> writePretty(rows, ps);
                case compact -> writeCompact(rows, ps);
                case ndjson -> writeNdjson(rows, ps);
            }
        }

        if (output != null) {
            LOG.info("Wrote {} rows to {}", rows.size(), output.getPath());
        }
    }

    private void writePretty(List<Map<String, Object>> rows, PrintStream ps) throws IOException {
        ps.println(prettyMapper.writeValueAsString(rows));
    }

    private void writeCompact(List<Map<String, Object>> rows, PrintStream ps) throws IOException {
        ps.println(compactMapper.writeValueAsString(rows));
    }

    private void writeNdjson(List<Map<String, Object>> rows, PrintStream ps) throws IOException {
        ObjectMapper ndjsonMapper = compactMapper.copy()
                .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        for (Map<String, Object> row : rows) {
            ps.println(ndjsonMapper.writeValueAsString(row));
        }
    }

    /** Wrapper that prevents closing stdout when used as an OutputStream. */
    private static class NonClosingOutputStream extends OutputStream {
        private final OutputStream delegate;

        NonClosingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() {
            // Don't close the underlying stream (stdout)
        }
    }
}
