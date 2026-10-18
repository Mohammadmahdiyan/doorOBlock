package com.doorblock.core.lists

import java.nio.ByteBuffer
import java.security.MessageDigest

class InvalidListException(message: String) : Exception(message)

/**
 * قالب فایل باینری:
 *   "DBLK" | version:int | stamp:long | count:int | count × long (مرتب، یکتا) | SHA-256 همه‌ی بایت‌های قبلی
 * هر فایل ناقص، خراب یا دست‌کاری‌شده رد می‌شود.
 */
object DomainSetCodec {
    private val MAGIC = byteArrayOf('D'.code.toByte(), 'B'.code.toByte(), 'L'.code.toByte(), 'K'.code.toByte())
    private const val VERSION = 1
    private const val HEADER = 4 + 4 + 8 + 4
    private const val DIGEST = 32
    const val MAX_COUNT = 20_000_000

    fun encode(set: DomainSet): ByteArray {
        val hashes = set.toHashArray()
        val payload = HEADER + 8 * hashes.size
        val buf = ByteBuffer.allocate(payload + DIGEST)
        buf.put(MAGIC)
        buf.putInt(VERSION)
        buf.putLong(set.stamp)
        buf.putInt(hashes.size)
        for (h in hashes) buf.putLong(h)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(buf.array(), 0, payload)
        buf.put(md.digest())
        return buf.array()
    }

    @Throws(InvalidListException::class)
    fun decode(bytes: ByteArray): DomainSet {
        if (bytes.size < HEADER + DIGEST) throw InvalidListException("too short")
        val buf = ByteBuffer.wrap(bytes)
        val magic = ByteArray(4)
        buf.get(magic)
        if (!magic.contentEquals(MAGIC)) throw InvalidListException("bad magic")
        if (buf.getInt() != VERSION) throw InvalidListException("bad version")
        val stamp = buf.getLong()
        val count = buf.getInt()
        if (count < 0 || count > MAX_COUNT) throw InvalidListException("bad count")
        val payload = HEADER.toLong() + 8L * count
        if (bytes.size.toLong() != payload + DIGEST) throw InvalidListException("bad size")
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes, 0, payload.toInt())
        val expected = md.digest()
        val actual = bytes.copyOfRange(payload.toInt(), bytes.size)
        if (!MessageDigest.isEqual(expected, actual)) throw InvalidListException("checksum mismatch")
        val hashes = LongArray(count)
        for (i in 0 until count) {
            hashes[i] = buf.getLong()
            if (i > 0 && hashes[i] <= hashes[i - 1]) throw InvalidListException("not sorted/unique")
        }
        return DomainSet.fromSortedUnique(hashes, stamp)
    }
}
