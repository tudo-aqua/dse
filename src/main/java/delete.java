import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class delete {
    public static void main(String[] args) {
        Pattern pattern = Pattern.compile("obj\\.method\\.of\\s+\\S+\\s+\"([^\"]*)\"\\s+\"([^\"]*)\"\\s+\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(" (assert (obj.method.of __object_0.cls \"foo\" \"()V\" \"LA;\")) ");
        System.out.println(matcher.find());
        System.out.println(matcher.group(1));
        System.out.println(matcher.group(2));
        System.out.println(matcher.group(3));
    }
}
