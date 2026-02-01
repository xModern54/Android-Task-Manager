package com.example.taskmanager;

interface IRootService {

    String getTestString();

    String getProcessList();

    String getProcessExtendedInfo(int pid);

    String getProcessDeepSnapshot(int pid);

    boolean sendSignal(int pid, int signal);

    String getKillCandidates();

    long executeKillTransaction(String packages);

    long getFreeRam();

    String getCpuSnapshotJson();

    String getVulkanInfoJson();

    String getGpuSnapshotJson();

    String getMemorySnapshotJson();
}

        

    
