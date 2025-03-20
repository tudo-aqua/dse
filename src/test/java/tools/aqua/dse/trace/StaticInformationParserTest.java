package tools.aqua.dse.trace;

import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParserException;
import org.testng.Assert;
import org.testng.annotations.Test;
import tools.aqua.dse.objects.ClazzModel;

import java.io.IOException;

public class StaticInformationParserTest {

    @Test
    public void testStaticInformationParser() throws IOException, SMTLIBParserException {

        String info =
                "class A { A() }" +
                "class C {}" +
                "class B extends A, C { B(), B(II) }";

        ClazzModel o = new ClazzModel(info);
        System.out.println(o);
        Assert.assertEquals(o.getClazzes().size(), 4);

    }

}
