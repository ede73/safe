package fi.iki.ede.backup

enum class RestorationProgress {
    BEGIN_RESTORATION,
    PROCESS_BACKUP,
    FINISHED_WITH_BACKUP,
    FAILED_ROLLBACK,
    RESTORING_OLD_BACKUP,
    REREAD_DATABASE,
    DONE
}
