package io.github.linuxkernel44.autocomp;

interface IProbeService {
    void destroy() = 16777114;
    /** Runs a shell command as the Shizuku user (shell) and returns stdout+stderr. */
    String exec(String command) = 1;
}
