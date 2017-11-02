/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2016 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.request.range.stream;

import org.apache.commons.io.IOUtils;
import org.artifactory.request.range.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.artifactory.request.range.ResponseWithRangeSupportHelper.MULTIPART_BYTERANGES_BOUNDRY_END;

/**
 * Returns multi sub-streams by wrapping the stream and skipping irrelevant bytes
 *
 * @author Gidi Shabat
 */
public class MultiRangeInputStream extends InputStream {
    public static final Logger log = LoggerFactory.getLogger(MultiRangeInputStream.class);
    private final int numberOfRanges;
    private List<Range> ranges;
    private InputStream inputStream;
    private long streamPointer = 0;
    private byte[] buffer;
    private int bufferPointer = 0;
    private Range currentRange;
    private boolean end;

    public MultiRangeInputStream(List<Range> ranges, InputStream inputStream) {
        this.ranges = ranges;
        numberOfRanges = ranges.size();
        if (ranges.size() < 2) {
            throw new RuntimeException(
                    "unsupported number of ranges: " + ranges.size() + ". The minimum number of ranges is " + 2 + ".");
        }
        this.inputStream = inputStream;
        // calculate response content length
    }

    @Override
    public int read() throws IOException {
        Range range = tryToPromoteRange();
        if (buffer != null) {
            byte result = buffer[bufferPointer];
            bufferPointer++;
            if (bufferPointer >= buffer.length) {
                buffer = null;
            }
            return result;
        }
        if (range == null) {
            // Make sure to read the file until EOF
            int result=inputStream.read();
            while (result>=0){
                result=inputStream.read();
            }
            return -1;
        }
        streamPointer++;
        return inputStream.read();
    }

    private Range tryToPromoteRange() throws IOException {
        // Need to skip bytes in stream
        if (currentRange != null && streamPointer < currentRange.getStart()) {
            while (currentRange != null && streamPointer < currentRange.getStart()) {
                inputStream.read();
                streamPointer++;
            }
            return currentRange;
        }
        // Need to streaming range
        if (currentRange != null && streamPointer >= currentRange.getStart() && streamPointer <= currentRange.getEnd()) {
            return currentRange;
        }
        // Need to promote range
        if (ranges.size() > 0 && (currentRange == null || streamPointer > currentRange.getEnd())) {
            currentRange = ranges.remove(0);
            buffer = currentRange.outputHeader(ranges.size() + 1 == numberOfRanges);
            bufferPointer = 0;
            return currentRange;
        }
        // Need to promote stream
        if (!end) {
            buffer = (MULTIPART_BYTERANGES_BOUNDRY_END + "\r\n").getBytes();
            bufferPointer = 0;
            end = true;
            currentRange = null;
            return null;
        }
        return null;
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(inputStream);
    }
}
