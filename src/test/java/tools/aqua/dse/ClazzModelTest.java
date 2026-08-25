package tools.aqua.dse;


import org.testng.annotations.Test;
import tools.aqua.dse.objects.Clazz;
import tools.aqua.dse.objects.ClazzModel;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

public class ClazzModelTest {
    @Test
    void testCorrectComputationOfAllSubclasses() {
//        String clazzStringToParse = "class LA; { LA;|()V, LA;|(II)V}\n" +
//                "class LB; extends LA;{ LB;|()V}\n" +
//                "class Ljava/lang/Integer; {}\n" +
//                "class Ljava/lang/String; {}";
//        String clazzStringToParse = "class LA; { LA;|()V, LA;|(II)V}\n" +
//                "class LB; extends LA;{ LB;|()V}\n";

        String clazzStringToParse = "class LA; extends LB; { LA;|()V }\n"
                        + "class LB; extends LC; { LB;|()V }\n"
                        + "class LC; { LC;|()V }\n"
                        + "class LD; extends LB; { LD;|()V }\n";

        ClazzModel clazzModel = new ClazzModel(clazzStringToParse);
        System.out.println(clazzModel);

        assertThat(clazzModel.getClazzes().get("LA;"))
                .hasFieldOrPropertyWithValue("name", "LA;")
                .hasFieldOrPropertyWithValue("superClazz", "LB;")
                .hasFieldOrPropertyWithValue("interfaces", new ArrayList<>())
                .hasFieldOrPropertyWithValue("directSubClazzes", new ArrayList<>())
                .hasFieldOrPropertyWithValue("allSubClazzes", new ArrayList<>())
                .hasFieldOrPropertyWithValue("constructors", Arrays.asList("LA;|()V"));


        assertThat(clazzModel.getClazzes().get("LB;"))
                .hasFieldOrPropertyWithValue("name", "LB;")
                .hasFieldOrPropertyWithValue("superClazz", "LC;")
                .hasFieldOrPropertyWithValue("interfaces", new ArrayList<>())
                .hasFieldOrPropertyWithValue("directSubClazzes", Arrays.asList("LA;", "LD;"))
                .hasFieldOrPropertyWithValue("allSubClazzes", Arrays.asList("LD;", "LA;"))
                .hasFieldOrPropertyWithValue("constructors", Arrays.asList("LB;|()V"));

        assertThat(clazzModel.getClazzes().get("LC;"))
                .hasFieldOrPropertyWithValue("name", "LC;")
                .hasFieldOrPropertyWithValue("superClazz", null)
                .hasFieldOrPropertyWithValue("interfaces", new ArrayList<>())
                .hasFieldOrPropertyWithValue("directSubClazzes", Arrays.asList("LB;"))
                .hasFieldOrPropertyWithValue("allSubClazzes", Arrays.asList("LD;", "LB;", "LA;"))
                .hasFieldOrPropertyWithValue("constructors", Arrays.asList("LC;|()V"));

        assertThat(clazzModel.getClazzes().get("LD;"))
                .hasFieldOrPropertyWithValue("name", "LD;")
                .hasFieldOrPropertyWithValue("superClazz", "LB;")
                .hasFieldOrPropertyWithValue("interfaces", new ArrayList<>())
                .hasFieldOrPropertyWithValue("directSubClazzes", new ArrayList<>())
                .hasFieldOrPropertyWithValue("allSubClazzes", new ArrayList<>())
                .hasFieldOrPropertyWithValue("constructors", Arrays.asList("LD;|()V"));
    }

    @Test
    void checkTestHierachy() {
        String clazzStringToParse = "class LA; { LA;|()V, LA;|(II)V}\n" +
                                    "class LB; extends LA;{ LB;|()V}";

        ClazzModel clazzModel = new ClazzModel(clazzStringToParse);
        System.out.println(clazzModel);

        assertThat(clazzModel.getClazzes().get("LA;"))
                .hasFieldOrPropertyWithValue("name", "LA;")
                .hasFieldOrPropertyWithValue("superClazz", null)
                .hasFieldOrPropertyWithValue("interfaces", new ArrayList<>())
                .hasFieldOrPropertyWithValue("directSubClazzes", Arrays.asList("LB;"))
                .hasFieldOrPropertyWithValue("allSubClazzes", Arrays.asList("LB;"))
                .hasFieldOrPropertyWithValue("constructors", Arrays.asList("LA;|()V", "LA;|(II)V"));

        assertThat(clazzModel.getClazzes().get("LB;"))
                .hasFieldOrPropertyWithValue("name", "LB;")
                .hasFieldOrPropertyWithValue("superClazz", "LA;")
                .hasFieldOrPropertyWithValue("interfaces", new ArrayList<>())
                .hasFieldOrPropertyWithValue("directSubClazzes", new ArrayList<>())
                .hasFieldOrPropertyWithValue("allSubClazzes", new ArrayList<>())
                .hasFieldOrPropertyWithValue("constructors", Arrays.asList("LB;|()V"));

    }

}
