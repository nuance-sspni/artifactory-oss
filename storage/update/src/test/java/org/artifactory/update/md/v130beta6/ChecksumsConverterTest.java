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

package org.artifactory.update.md.v130beta6;

import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.update.md.MetadataConverterTest;
import org.artifactory.util.ResourceUtils;
import org.artifactory.util.XmlUtils;
import org.jdom2.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Set;

/**
 * Tests the conversion of checksums info in the file metadata.
 *
 * @author Yossi Shaul
 */
@Test
public class ChecksumsConverterTest extends MetadataConverterTest {
    private static final Logger log = LoggerFactory.getLogger(ChecksumsConverterTest.class);

    public void convertValidFile() throws Exception {
        String fileMetadata = "/metadata/v130beta6/artifactory-file.xml";
        Document doc = convertXml(fileMetadata, new ChecksumsConverter());

        String result = XmlUtils.outputString(doc);
        log.debug(result);

        // the result is intermediate so it might not be compatible with latest FileInfo
        // but for now it is a good test to test the resulting FileInfo 
        MutableFileInfo fileInfo = (MutableFileInfo) xstream.fromXML(result);
        Set<ChecksumInfo> checksums = fileInfo.getChecksums();
        Assert.assertNotNull(checksums);
        Assert.assertEquals(checksums.size(), 2);
        Assert.assertEquals(fileInfo.getSha1(), "99129f16442844f6a4a11ae22fbbee40b14d774f");
        Assert.assertEquals(fileInfo.getMd5(), "1f40fb782a4f2cf78f161d32670f7a3a");

        FileInfo expected = (FileInfo) xstream.fromXML(
                ResourceUtils.getResource("/metadata/v130beta6/artifactory-file-expected.xml"));
        Assert.assertTrue(fileInfo.isIdentical(expected));
    }
}
