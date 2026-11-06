package tools.aqua.dse;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorConstructorTest {

    @Test
    void conversionsOfArgumentsAreRemoved() {
        assertThat(Executor.unwrapConversions("<>LS;|(CI)V|{((_ zero_extend 16) __object_0__char_0)}{__object_0__int_0}"))
                .isEqualTo("<>LS;|(CI)V|{__object_0__char_0}{__object_0__int_0}");
        assertThat(Executor.unwrapConversions("{((_ sign_extend 24) __object_1__byte_0)}{((_ extract 15 0) ((_ sign_extend 16) x))}"))
                .isEqualTo("{__object_1__byte_0}{x}");
        assertThat(Executor.unwrapConversions("<>LMain;|()V|")).isEqualTo("<>LMain;|()V|");
    }
}
