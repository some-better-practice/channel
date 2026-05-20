package com.tsmc.autochannel.component;

import com.tsmc.autochannel.config.Stdf2CsvConfig;
import com.tsmc.autochannel.metrics.ProcessingMetrics;
import com.tsmc.autochannel.service.ObjectStorageService;
import com.tsmc.autochannel.service.TransferLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Stdf2CsvComponentTest {

    @Mock private ObjectStorageService minioService;
    @Mock private ProcessingMetrics metrics;
    @Mock private TransferLogService transferLogService;
    @Mock private ProcessStarter processStarter;
    @Mock private Process process;

    private Stdf2CsvComponent component;

    @BeforeEach
    void setUp() {
        component = new Stdf2CsvComponent(minioService, metrics, transferLogService, processStarter);
        ReflectionTestUtils.setField(component, "timeoutSeconds", 5L);
    }

    // ── timeout ───────────────────────────────────────────────────────────────

    @Test
    void runConversion_timeout_throwsTimeoutExceptionAndKillsProcess() throws Exception {
        when(processStarter.start(any())).thenReturn(process);
        when(process.waitFor(anyLong(), any())).thenReturn(false);

        assertThrows(TimeoutException.class, () ->
                component.runConversion("input.stdf", "output.csv", Stdf2CsvConfig.Converter_TYPE.TESTNAME));

        verify(process).destroyForcibly();
    }

    // ── success ───────────────────────────────────────────────────────────────

    @Test
    void runConversion_exitCodeZero_completesNormally() throws Exception {
        when(processStarter.start(any())).thenReturn(process);
        when(process.waitFor(anyLong(), any())).thenReturn(true);
        when(process.exitValue()).thenReturn(0);

        assertDoesNotThrow(() ->
                component.runConversion("input.stdf", "output.csv", Stdf2CsvConfig.Converter_TYPE.TESTNO));
    }

    // ── non-zero exit code ────────────────────────────────────────────────────

    @Test
    void runConversion_nonZeroExitCode_throwsRuntimeException() throws Exception {
        when(processStarter.start(any())).thenReturn(process);
        when(process.waitFor(anyLong(), any())).thenReturn(true);
        when(process.exitValue()).thenReturn(1);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                component.runConversion("input.stdf", "output.csv", Stdf2CsvConfig.Converter_TYPE.NONCHANNEL));

        assertTrue(ex.getMessage().contains("code 1"));
    }

    // ── interrupt ─────────────────────────────────────────────────────────────

    @Test
    void runConversion_interrupted_destroysProcessAndSetsInterruptFlag() throws Exception {
        when(processStarter.start(any())).thenReturn(process);
        when(process.waitFor(anyLong(), any())).thenThrow(new InterruptedException());

        assertThrows(InterruptedException.class, () ->
                component.runConversion("input.stdf", "output.csv", Stdf2CsvConfig.Converter_TYPE.NONMERGE));

        verify(process).destroyForcibly();
        assertTrue(Thread.interrupted(), "interrupt flag should be restored");
    }

    // ── ProcessBuilder 命令參數 ────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(Stdf2CsvConfig.Converter_TYPE.class)
    void getStdf2CsvBuilder_allConverterTypes_returnsNonEmptyCommand(Stdf2CsvConfig.Converter_TYPE type) {
        ProcessBuilder pb = component.getStdf2CsvBuilder("in.stdf", "out.csv", type);

        assertNotNull(pb);
        assertFalse(pb.command().isEmpty());
        assertEquals("dotnet", pb.command().get(0));
        assertEquals("stdf2csv.dll", pb.command().get(1));
    }

    @Test
    void getStdf2CsvBuilder_testname_containsMFlag() {
        ProcessBuilder pb = component.getStdf2CsvBuilder("in.stdf", "out.csv", Stdf2CsvConfig.Converter_TYPE.TESTNAME);
        assertTrue(pb.command().contains("/M"));
    }

    @Test
    void getStdf2CsvBuilder_testno_containsNFlag() {
        ProcessBuilder pb = component.getStdf2CsvBuilder("in.stdf", "out.csv", Stdf2CsvConfig.Converter_TYPE.TESTNO);
        assertTrue(pb.command().contains("/N"));
    }

    @Test
    void getStdf2CsvBuilder_nonchannel_containsCNFlag() {
        ProcessBuilder pb = component.getStdf2CsvBuilder("in.stdf", "out.csv", Stdf2CsvConfig.Converter_TYPE.NONCHANNEL);
        assertTrue(pb.command().contains("/CN"));
    }

    @Test
    void getStdf2CsvBuilder_nonmerge_doesNotContainChannelFlag() {
        ProcessBuilder pb = component.getStdf2CsvBuilder("in.stdf", "out.csv", Stdf2CsvConfig.Converter_TYPE.NONMERGE);
        assertFalse(pb.command().contains("/M"));
        assertFalse(pb.command().contains("/N"));
        assertFalse(pb.command().contains("/CN"));
    }
}
