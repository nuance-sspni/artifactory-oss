//package org.artifactory.environment.converter.local.version.v1;
//
//import org.apache.commons.io.FileUtils;
//import org.artifactory.common.ArtifactoryHome;
//import org.artifactory.common.config.bootstrap.BootstrapBundle;
//import org.artifactory.common.config.bootstrap.BootstrapBundleHandle;
//import org.artifactory.environment.converter.local.version.LocalEnvironmentVersion;
//import org.artifactory.version.ArtifactoryVersion;
//import org.artifactory.version.CompoundVersionDetails;
//import org.testng.annotations.AfterMethod;
//import org.testng.annotations.BeforeMethod;
//import org.testng.annotations.DataProvider;
//import org.testng.annotations.Test;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.stream.Stream;
//
//import static java.util.Arrays.asList;
//import static java.util.Collections.singleton;
//import static org.testng.Assert.*;
//
///**
// * FIXME [YA] Reimplement DeployBootstrapBundleConverterTest
// * Created by Yinon Avraham.
// */
//public class DeployBootstrapBundleConverterTest {
//
//    private File homeDir;
//    private DeployBootstrapBundleConverter converter = new DeployBootstrapBundleConverter();
//
//    @BeforeMethod
//    public void setup() throws Exception {
//        homeDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();
//    }
//
//    @AfterMethod
//    public void cleanup() throws Exception {
//        FileUtils.forceDelete(homeDir);
//    }
//
//    @Test
//    public void testConvertFrom4xTo5x() throws Exception {
//        File file1 = saveFile("foo/file1", "new content1");
//        File file3 = saveFile("bar/file3", "new content3");
//        File file5 = saveFile("file5", "new content5");
//        File bundleFile = createBootstrapBundleFile(file1, file3, file5);
//        saveFile(file1, "old content1");
//        assertTrue(file3.delete());
//        saveFile(file5, "old content5");
//        File file2 = saveFile("foo/file2", "old content2");
//        File file4 = saveFile("bar/file4", "old content4");
//
//        CompoundVersionDetails source = new CompoundVersionDetails(ArtifactoryVersion.v4140, "4.14.0", "4140", 4140);
//        CompoundVersionDetails target = new CompoundVersionDetails(ArtifactoryVersion.v500beta1, "5.0.0-beta-1", "5000", 5000);
//        runConversion(new ArtifactoryHome(homeDir), source, target);
//
//        assertFileContentEquals(file1, "new content1");
//        assertFileContentEquals(file2, "old content2");
//        assertFileContentEquals(file3, "new content3");
//        assertFileContentEquals(file4, "old content4");
//        assertFileContentEquals(file5, "new content5");
//        assertFileCopyExists(file1);
//        assertFileCopyExists(file5);
//        assertFalse(bundleFile.exists(), "Bundle file is expected to be deleted");
//    }
//
//    @Test
//    public void testConvertFromSame5xVersion() throws Exception {
//        File file1 = saveFile("foo/file1", "new content1");
//        File file3 = saveFile("bar/file3", "new content3");
//        File file5 = saveFile("file5", "new content5");
//        File bundleFile = createBootstrapBundleFile(file1, file3, file5);
//        asList(file1, file3, file5).forEach(File::delete);
//
//        //In fresh installation, both the source and target versions are the current version.
//        CompoundVersionDetails current = new CompoundVersionDetails(ArtifactoryVersion.v500beta1, "5.0.0", "5000", 5000);
//        runConversion(new ArtifactoryHome(homeDir), current, current);
//
//        assertFileContentEquals(file1, "new content1");
//        assertFileContentEquals(file3, "new content3");
//        assertFileContentEquals(file5, "new content5");
//        assertFalse(bundleFile.exists(), "Bundle file is expected to be deleted");
//    }
//
//    @Test
//    public void testConvertFromSame5xVersionWithFilesIsIgnored() throws Exception {
//        File file1 = saveFile("foo/file1", "new content1");
//        File file3 = saveFile("bar/file3", "new content3");
//        File file5 = saveFile("file5", "new content5");
//        createBootstrapBundleFile(file1, file3, file5);
//        saveFile(file1, "old content1");
//        assertTrue(file3.delete());
//        saveFile(file5, "old content5");
//        File file2 = saveFile("foo/file2", "old content2");
//        File file4 = saveFile("bar/file4", "old content4");
//
//        CompoundVersionDetails current = new CompoundVersionDetails(ArtifactoryVersion.v500beta1, "5.0.0", "5000", 5000);
//        runConversion(new ArtifactoryHome(homeDir), current, current);
//
//        assertFileContentEquals(file1, "old content1");
//        assertFileContentEquals(file2, "old content2");
//        assertFalse(file3.exists(), "File is expected to be deleted: " + file3);
//        assertFileContentEquals(file4, "old content4");
//        assertFileContentEquals(file5, "old content5");
//    }
//
//    @Test
//    public void testConvert4xTo5xWithoutBundleFileIsNoOp() throws Exception {
//        CompoundVersionDetails source = new CompoundVersionDetails(ArtifactoryVersion.v4142, "4.14.2", "4142", 4142);
//        CompoundVersionDetails target = new CompoundVersionDetails(ArtifactoryVersion.v500beta1, "5.0.0", "5000", 5000);
//        runConversion(new ArtifactoryHome(homeDir), source, target);
//    }
//
//    @Test
//    public void testConvert5xTo5xWithoutBundleFileIsNoOp() throws Exception {
//        CompoundVersionDetails current = new CompoundVersionDetails(ArtifactoryVersion.v500beta1, "5.0.0", "5000", 5000);
//        runConversion(new ArtifactoryHome(homeDir), current, current);
//    }
//
//    @Test
//    public void testBundleFileInHaEtcAndUpgrade() {
//        //TODO [by dan]:
//    }
//
//    @Test
//    public void testBundleFileInHaEtcAndNewInstall() {
//        //TODO [by dan]:
//    }
//
//    /*@Test(dataProvider = "provideSourceTargetVersions")
//    public void testIsInterested(CompoundVersionDetails source, CompoundVersionDetails target) throws Exception {
//        assertTrue(converter.isInterested(new ArtifactoryHome(homeDir), source, target));
//    }
//
//    @DataProvider(name = "provideSourceTargetVersions")
//    public static Object[][] provideSourceTargetVersions() {
//        CompoundVersionDetails v4142 = new CompoundVersionDetails(ArtifactoryVersion.v4142, "4.14.2", "4142", 4142);
//        CompoundVersionDetails v500 = new CompoundVersionDetails(ArtifactoryVersion.v500beta1, "5.0.0", "5000", 5000);
//        return new Object[][] {
//                { v4142, v500 },
//                { v500, v500 },
//        };
//    }*/
//
//    /**
//     * {@link LocalEnvironmentVersion} tests each converter for isInterested() and only then runs it.
//     */
//    private void runConversion(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
//        if (converter.isInterested(home, source, target)) {
//            converter.convert(home, source, target);
//        }
//    }
//
//    private void assertFileCopyExists(File file) {
//        boolean copyExists = Stream.of(file.getParentFile().listFiles())
//                .anyMatch(f -> f.getName().startsWith(file.getName() + ".bootstrap."));
//        assertTrue(copyExists, "A copy of the original file does not exist for file: " + file);
//    }
//
//    private void assertFileContentEquals(File file, String content) throws IOException {
//        assertEquals(Files.readAllLines(file.toPath()).get(0), content);
//    }
//
//    private File saveFile(String path, String content) throws IOException {
//        return saveFile(homeDir.toPath().resolve(path), content);
//    }
//
//    private File saveFile(File file, String content) throws IOException {
//        return saveFile(file.toPath(), content);
//    }
//
//    private File saveFile(Path path, String content) throws IOException {
//        path.toFile().getParentFile().mkdirs();
//        return Files.write(path, singleton(content)).toFile();
//    }
//
//    private File createBootstrapBundleFile(File... files) {
//        BootstrapBundleHandle bundleHandle = BootstrapBundle.startBundle(homeDir);
//        Stream.of(files).forEach(bundleHandle::addFile);
//        File bundleFile = new File(homeDir, "etc/" + ArtifactoryHome.BOOTSTRAP_BUNDLE_FILENAME);
//        bundleFile.getParentFile().mkdirs();
//        bundleHandle.saveBundle(bundleFile);
//        return bundleFile;
//    }
//}