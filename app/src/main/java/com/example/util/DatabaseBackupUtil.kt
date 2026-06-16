package com.example.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DatabaseBackupUtil {

    private const val DB_NAME = "balance_tracker_database"

    fun backupDatabase(context: Context, backupUri: Uri): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            context.contentResolver.openOutputStream(backupUri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    val filesToBackup = listOf(dbFile, walFile, shmFile)
                    for (file in filesToBackup) {
                        if (file.exists()) {
                            val entry = ZipEntry(file.name)
                            zos.putNextEntry(entry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restoreDatabase(context: Context, backupUri: Uri): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            val dbDir = dbFile.parentFile ?: return false
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            context.contentResolver.openInputStream(backupUri)?.use { inputStream ->
                val bufferedStream = java.io.BufferedInputStream(inputStream)
                bufferedStream.mark(4)
                val magic = ByteArray(4)
                val read = bufferedStream.read(magic)
                bufferedStream.reset()

                var isZip = false
                if (read == 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()) {
                    isZip = true
                }

                if (isZip) {
                    ZipInputStream(bufferedStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val outFile = File(dbDir, entry.name)
                            // Make sure we only restore the allowed files
                            if (entry.name == DB_NAME || entry.name == "$DB_NAME-wal" || entry.name == "$DB_NAME-shm") {
                                FileOutputStream(outFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } else {
                    FileOutputStream(dbFile).use { fos ->
                        bufferedStream.copyTo(fos)
                    }
                    if (walFile.exists()) walFile.delete()
                    if (shmFile.exists()) shmFile.delete()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
