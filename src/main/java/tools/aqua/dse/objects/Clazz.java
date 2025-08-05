package tools.aqua.dse.objects;

import java.util.Arrays;

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
    private final String[] superClasses;
    private final String[] constructors;

    public Clazz(String name,
                 String[] superClasses,
                 String[] constructors) {
        this.name = name;
        this.superClasses = superClasses;
        this.constructors = constructors;
    }

    public String getName() {
        return name;
    }

    public String[] getSuperClasses() {
        return superClasses;
    }

    public String[] getConstructors() {
        return constructors;
    }

    /**
     * Evaluates whether the given class or null is a superclass of the class
     * @param className  name of the class for which shall be checked whether it is a superclass of this class
     * @return           true, if className is a superclass of the caller (this)
     *                   false, otherwise
     */
    public boolean isSuperClazz(String className) {
        return name.equals(className) || Arrays.asList(superClasses).contains(className);
    }

    /**
     * Evaluates wether the given class or null is an instance of the class
     * @param className name of the class or null for which shall be checked whether it is a instance of this class
     * @return          false, if the caller (this) class is "null"
     *                  true, if the name of this is not "null" and the className is a superclass of the caller (this)
     *                  false, otherwise
     */
    public boolean isInstanceOf(String className) {
        if (this.name.equals("null")) {
            return false;
        }
        return isSuperClazz(className);
    }

    /**
     * Evaluates whether the given constructor belongs to this class.
     * @param constructorName   name of the constructor for which shall be checked whether it belongs to this class
     * @return                  true, this constructor belongs to this class
     *                          false, otherwise
     */
    public boolean hasConstructor(String constructorName) {
        return Arrays.asList(constructors).contains(constructorName);
    }

    @Override
    public String toString() {
        return "Clazz{" +
                "name='" + name + '\'' +
                ", superClasses=" + Arrays.toString(superClasses) +
                ", constructors=" + Arrays.toString(constructors) +
                '}';
    }
}
