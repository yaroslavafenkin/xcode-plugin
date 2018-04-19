package au.com.rayh;

import org.junit.Assert;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import java.util.HashMap;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;


/**
 * Created by timothy on 9/6/14.
 */
public class XcodeProjectParserTest {

    @Test
    public void testValidProject() throws Throwable {
        String projectLocation = URLDecoder.decode(XcodeProjectParserTest.class.getClassLoader().getResource("TestXcodeProject.xcodeproj").getPath(), "UTF-8");
        HashMap<String, ProjectScheme> xcodeSchemes = XcodeProjectParser.listXcodeSchemes(projectLocation);
        Assert.assertEquals(1, xcodeSchemes.size());
        String xcodeSchema = "TestXcodeProject";
        Assert.assertTrue(xcodeSchemes.containsKey(xcodeSchema));
        ProjectScheme projectScheme = xcodeSchemes.get(xcodeSchema);
        Assert.assertEquals("container:TestXcodeProject.xcodeproj", projectScheme.referencedContainer);
        Assert.assertEquals("TestXcodeProject", projectScheme.blueprintName);
        String workspaceLocation = XcodeProjectParserTest.class.getClassLoader().getResource("TestXcodeProject.xcworkspace").getPath();
        List<String> projectList = XcodeProjectParser.parseXcodeWorkspace(workspaceLocation);
        Assert.assertEquals(1, projectList.size());
        Assert.assertTrue(projectList.contains("TestXcodeProject.xcodeproj"));
        XcodeProject xcodeProject = XcodeProjectParser.parseXcodeProject(projectLocation);
        Assert.assertNotNull(xcodeProject.projectTarget.get("TestXcodeProject"));
        Assert.assertNotNull(xcodeProject.projectTarget.get("TestXcodeProjectTests"));
        Assert.assertNotNull(xcodeProject.projectTarget.get("TestXcodeProjectTests"));
    }

    @Test
    public void testInvalidProject() throws Throwable {

    }

}
