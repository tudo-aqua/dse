package tools.aqua.dse.objects;

import java.util.Arrays;
import java.util.List;

/**
 * This class collects static information of a class such as
 * <ul>
 *     <li>name</li>
 *     <li>all super classes</li>
 *     <li>all constructors</li>
 * </ul>
 */
public class Clazz {

    private final String name;
    private String superClazz;
    private List<String> interfaces;
    private List<String> directSubClazzes;
    private List<String> allSubClazzes;
    private List<String> constructors;

    public Clazz(String name,
                 String superClazz,
                 List<String> interfaces,
                 List<String> constructors) {
        this.name = name;
        this.superClazz = superClazz;
        this.interfaces = interfaces;
        this.constructors = constructors;
    }

    public Clazz(String name, //todo: nessary
                 String superClazz,
                 List<String> interfaces,
                 List<String> directSubClazzes,
                 List<String> allSubClazzes,
                 List<String> constructors) {
        this.name = name;
        this.superClazz = superClazz;
        this.interfaces = interfaces;
        this.directSubClazzes = directSubClazzes;
        this.allSubClazzes = allSubClazzes;
        this.constructors = constructors;
    }

    public void setDirectSubClazzes(List<String> directSubClazzes) {
        this.directSubClazzes = directSubClazzes;
    }

    public void setAllSubClazzes(List<String> allSubClazzes) {
        this.allSubClazzes = allSubClazzes;
    }

    public String getName() {
        return name;
    }

    public String getSuperClazz() {
        return superClazz;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public List<String> getDirectSubClazzes() {
        return directSubClazzes;
    }

    public List<String> getAllSubClazzes() {
        return allSubClazzes;
    }

    public List<String> getConstructors() {
        return constructors;
    }

//    /**
//     * Evaluates whether the given class or null is a superclass of the class
//     * @param className  name of the class for which shall be checked whether it is a superclass of this class
//     * @return           true, if className is a superclass of the caller (this)
//     *                   false, otherwise
//     */
//    public boolean isSuperClazz(String className) {
//        return name.equals(className) || Arrays.asList(superClazz).contains(className);
//    }
    public boolean isCastable(Clazz clazz2) {
        String nameClazz1 = this.getName();
        String nameClazz2 = clazz2.getName();

        return nameClazz1.equals("null") || nameClazz1.equals(nameClazz2) || clazz2.getAllSubClazzes().contains(nameClazz1);
    }

    /**
     * Evaluates wether the given class or null is an instance of the class
     * @param className name of the class or null for which shall be checked whether it is a instance of this class
     * @return          false, if the caller (this) class is "null"
     *                  true, if the name of this is not "null" and the className is a superclass of the caller (this)
     *                  false, otherwise
     */
    public boolean isInstanceOf(Clazz clazz2) {
        if (this.name.equals("null")) {
            return false;
        }
        return isCastable(clazz2);
    }

    /**
     * Evaluates whether the given constructor belongs to this class.
     * @param constructorName   name of the constructor for which shall be checked whether it belongs to this class
     * @return                  true, this constructor belongs to this class
     *                          false, otherwise
     */
    public boolean hasConstructor(String constructorName) {
        return constructors.contains(constructorName);
    }

    @Override
    public String toString() {
        return "Clazz{" +
                "name='" + name + '\'' +
                ", superClazz='" + superClazz + '\'' +
                ", interfaces=" + interfaces +
                ", directSubClazzes=" + directSubClazzes +
                ", allSubClazzes=" + allSubClazzes +
                ", constructors=" + constructors +
                '}';
    }
}
