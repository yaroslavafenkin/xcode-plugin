package au.com.rayh;

import org.junit.Assert;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import java.util.HashMap;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Arrays;
import org.junit.Assume;
import hudson.FilePath;
import hudson.Launcher.LocalLauncher;
import hudson.util.StreamTaskListener;

/**
 * Created by Kazuhide Takahashi on 1/22/18.
 */
public class XcodeProjectParserTest {
    @Test
    public void testValidProject() throws Throwable {
	File f = new File("./");
	FilePath workspace = new FilePath(f);
	String projectLocation = URLDecoder.decode(XcodeProjectParserTest.class.getClassLoader().getResource("XcodeProject.tar.gz").getPath(), "UTF-8");
	run(workspace, "tar", "zxvpf", projectLocation);
	FilePath projectPath = workspace.child("TestXcodeProject.xcodeproj");
        HashMap<String, ProjectScheme> xcodeSchemes = XcodeProjectParser.listXcodeSchemes(projectPath);
        Assert.assertEquals(1, xcodeSchemes.size());
        String xcodeSchema = "TestXcodeProject";
        Assert.assertTrue(xcodeSchemes.containsKey(xcodeSchema));
        ProjectScheme projectScheme = xcodeSchemes.get(xcodeSchema);
        Assert.assertEquals("container:TestXcodeProject.xcodeproj", projectScheme.referencedContainer);
        Assert.assertEquals("TestXcodeProject", projectScheme.blueprintName);
	FilePath workspacePath = workspace.child("TestXcodeProject.xcworkspace");
        List<String> projectList = XcodeProjectParser.parseXcodeWorkspace(workspacePath);
        Assert.assertEquals(1, projectList.size());
        Assert.assertTrue(projectList.contains("TestXcodeProject.xcodeproj"));
        XcodeProject xcodeProject = XcodeProjectParser.parseXcodeProject(projectPath);
        Assert.assertNotNull(xcodeProject.projectTarget.get("TestXcodeProject"));
        Assert.assertNotNull(xcodeProject.projectTarget.get("TestXcodeProjectTests"));
        Assert.assertNotNull(xcodeProject.projectTarget.get("TestXcodeProjectTests"));
    }

    @Test
    public void testInvalidProject() throws Throwable {

    }

    private static void run(FilePath dir, String... cmds) throws InterruptedException {
        try {
            Assert.assertEquals(0, new LocalLauncher(StreamTaskListener.fromStdout()).launch().cmds(cmds).pwd(dir).join());
        } catch (IOException x) { // perhaps restrict to x.message.contains("Cannot run program")? or "error=2, No such file or directory"?
            Assume.assumeNoException("failed to run " + Arrays.toString(cmds), x);
        }
    }
}
