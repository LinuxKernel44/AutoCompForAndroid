package io.github.linuxkernel44.autocomp;

interface IShellService {
    void destroy() = 16777114;
    /** Lines of `dumpsys audio` describing playback configurations (runs as shell). */
    String dumpAudio() = 1;
    /** Grants RECORD_AUDIO (level meter) to AutoComp itself. Returns command output. */
    String grantRecordAudio() = 2;
}
