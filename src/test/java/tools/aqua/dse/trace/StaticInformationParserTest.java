package tools.aqua.dse.trace;

import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParserException;
import org.testng.Assert;
import org.testng.annotations.Test;
import tools.aqua.dse.objects.ClazzModel;

import java.io.IOException;

public class StaticInformationParserTest {

    @Test(enabled = false) // This does not work @Marvin
    public void testStaticInformationParser() throws IOException, SMTLIBParserException {

        String info =
                "class LA; { LA;()V }" +
                "class LC; {}" +
                "class LB; extends LA;, LC; { LB;()V, LB;(II)V }";

        ClazzModel o = new ClazzModel(info);
        System.out.println(o);
        Assert.assertEquals(o.getClazzes().size(), 4); // (classes: LA; LB; LC; NULL)

    }

//class LA; { LA;|()V }
//class LC; {}
//class LB; extends LA;, LC; { LB;|()V, LB;|(II)V }
}
