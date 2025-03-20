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
     * Evaluates whether the given class is a superclass of the class
     * @param className  name of the class for which shall be checked whether it is a superclass of this class
     * @return           true, given class is a superclass of this class
     *                   false, otherwise
     */
    public boolean isSuperClazz(String className) {
        return name.equals(className) || Arrays.asList(superClasses).contains(className);
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
