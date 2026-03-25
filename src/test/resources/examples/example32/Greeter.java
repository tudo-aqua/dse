public interface Greeter {
	default void greet() {
		System.out.println("Hello World!"); 
	}
}
