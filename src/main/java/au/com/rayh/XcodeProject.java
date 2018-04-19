package au.com.rayh;

//import org.kohsuke.stapler.DataBoundConstructor;
import java.util.HashMap;

public class XcodeProject {
 
    //public String uuid;
    //public String name;
    public String file;
    public HashMap<String, ProjectTarget> projectTarget;

    XcodeProject() {
	//uuid = null;
	//name = null;
	file = null;
	projectTarget = new HashMap<String, ProjectTarget>();
    }
}

