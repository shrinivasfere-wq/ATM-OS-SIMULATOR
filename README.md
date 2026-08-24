# ATM-OS Simulator — Unified Edition v2.0

Combines the JavaFX dashboard visuals with the full OS-concept depth of the terminal version.

## How to Run
```bash
mvn javafx:run
```
Requires JDK 17+ and Maven. JavaFX 21 is auto-downloaded.

## Accounts & PINs
| Account | Name          | PIN  | Balance   |
|---------|---------------|------|-----------|
| ACC001  | Alice Kumar   | 1111 | Rs.50,000 |
| ACC002  | Bob Sharma    | 2222 | Rs.25,000 |
| ACC003  | Charlie Singh | 3333 | Rs.10,000 |
| ACC004  | David Mehta   | 4444 | Rs.18,000 |
| ACC005  | Eva Patel     | 5555 | Rs.91,000 |

## Six Dashboard Tabs

### 1. ATM Terminals
Three interactive ATM panels side-by-side. Full flow: Account ID → PIN (lockout after 3 fails) →
Transaction menu (Withdraw, Deposit, Transfer, Balance, Mini-Statement, Change PIN) → Real result shown.
Every transaction: creates an OSProcess, acquires a semaphore mutex, writes WAL, commits or rollbacks.

### 2. Scheduler Monitor
Live tick loop running at 500ms. Shows current running transaction, ready queue, Canvas Gantt chart
(coloured blocks per ATM), Canvas CPU utilization graph, WAL journal log, active semaphore locks.
Algorithm switchable live: FCFS / Round Robin / Priority.

### 3. Memory Manager
Live page table from MemoryManager (16 physical frames). Frame grid canvas — green = used, purple = kernel.
Page fault simulation button. Dirty bit tracking on every write.

### 4. CPU Scheduler Sim
Educational scheduler. Add processes manually (name, arrival, burst, priority). Run FCFS, Round Robin,
Priority non-preemptive, Priority preemptive, or Compare ALL. Canvas Gantt chart + metrics table.

### 5. Race Condition Demo
Side-by-side: WITHOUT MUTEX (race condition) vs WITH MUTEX (thread-safe). Real Java threads
(ExecutorService + CountDownLatch). Streaming log shows each ATM read/write live. Final balance
shown in red (wrong) or green (correct).

### 6. Interrupt Handler
Six ISR buttons: CARD_TIMEOUT, WRONG_PIN, NETWORK_FAILURE, CASH_JAM, POWER_FAILURE, INVALID_AMOUNT.
Each fires InterruptHandler on a background thread, streams ISR log lines to the UI.
Random Interrupt button. All interrupts logged to TransactionLog.

## OS Concepts Demonstrated
| Concept                | Implementation                                          |
|------------------------|---------------------------------------------------------|
| Process / PCB          | OSProcess — state, priority, cpuTime, uptime, owner     |
| CPU Scheduling         | SchedulerEngine (live) + CPUScheduler (educational sim) |
| Real Threads           | ATMProcess auto-sim, MultiATM race demo, ISR threads    |
| Mutual Exclusion       | SemaphoreManager — acquire/release with history         |
| Deadlock Prevention    | Lock ordering in transfer (alphabetical account ID)     |
| Deadlock Detection     | DeadlockDetector — simulated circular wait + resolution |
| Paging / Page Faults   | MemoryManager — 16 frames, FIFO swap, dirty bits        |
| Interrupts / ISR       | InterruptHandler — 6 types, ISR simulation              |
| Write-Ahead Logging    | TransactionLog — WAL-BEGIN, COMMITTED, ROLLBACK entries |
| System Monitor         | SystemMonitor — CPU%, queue size, avg wait/TAT          |

## Package Structure
```
ATM_OS_MERGED/
├── atm/          Account.java, Bank.java
├── controller/   ATMController.java  (bridges UI ↔ Kernel)
├── fs/           TransactionLog.java (WAL)
├── kernel/       Kernel.java, InterruptHandler.java
├── memory/       MemoryManager.java, PageEntry.java
├── monitor/      SystemMonitor.java
├── process/      OSProcess.java, ProcessManager.java, Scheduler.java, ProcessState.java
├── scheduler/    SchedulerEngine.java, Transaction.java, TransactionType.java,
│                 CPUScheduler.java, SchedulerProcess.java, GanttEntry.java, SchedulingAlgorithm.java
├── sync/         SemaphoreManager.java, DeadlockDetector.java
├── ui/           DashboardApp.java, ATMPanel.java, MemoryPanel.java,
│                 SchedulerPanel.java, RacePanel.java, InterruptPanel.java
├── Main.java
└── pom.xml
```
