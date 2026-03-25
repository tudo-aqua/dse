import tools.aqua.concolic.*;

public class Factories {

    public static class AFactory implements ObjectFactory<A> {
        @Override
        public A createObject() {
            final int i = Verifier.nondetInt();
            switch (i) {
                case 1:
                    return new A();
                case 2:
                    return new A(Verifier.nondetInt());
                case 3:
                    return new A(Verifier.nondetInt(), Verifier.nondetInt());
                case 4:
                    return new A(Verifier.nondetInt(), Verifier.nondetInt(), new SubFactory().createObject());
                case 5:
                    return new BFactory().createObject();
                case 6:
                    return new CFactory().createObject();
                default:
                    return null;
            }
        }
    }

    public static class BFactory implements ObjectFactory<B> {
        @Override
        public B createObject() {
            final int i = Verifier.nondetInt();
            switch (i) {
                case 1:
                    return new B();
                default:
                    return null;
            }
        }
    }

    public static class CFactory implements ObjectFactory<C> {
        @Override
        public C createObject() {
            final int i = Verifier.nondetInt();
            switch (i) {
                case 1:
                    return new C();
                default:
                    return null;
            }
        }
    }

    public static class SubFactory implements ObjectFactory<Sub> {
        @Override
        public Sub createObject() {
            final int i = Verifier.nondetInt();
            switch (i) {
                case 1:
                    return new Sub1Factory().createObject();
                case 2:
                    return new Sub2Factory().createObject();
                default:
                    return null;
            }
        }
    }

    public static class Sub1Factory implements ObjectFactory<Sub1> {
        @Override
        public Sub1 createObject() {
            final int i = Verifier.nondetInt();
            switch (i) {
                case 1:
                    return new Sub1();
                default:
                    return null;
            }
        }
    }

    public static class Sub2Factory implements ObjectFactory<Sub2> {
        @Override
        public Sub2 createObject() {
            final int i = Verifier.nondetInt();
            switch (i) {
                case 1:
                    return new Sub2();
                case 2:
                    return new Sub2(Verifier.nondetInt());
                default:
                    return null;
            }
        }
    }

    public static class GreeterFactory implements ObjectFactory<Greeter> {
        @Override
        public Greeter createObject() {
            final int i = Verifier.nondetInt();
            switch (i) {
                case 1:
                    return new AFactory().createObject();
                default:
                    return null;
            }
        }
    }
}