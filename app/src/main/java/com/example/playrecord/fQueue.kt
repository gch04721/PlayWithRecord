package com.example.playrecord

import android.util.Log
import java.util.*

/**
 * Created by root on 18. 3. 7.
 *
 * This data structure is a simple queue. This just implements a FIFO queue in JAVA
 *
 *
 */
class fQueue(size: Int) {
    private var queue: MutableList<ByteArray>?
    private val size: Int
    @Throws(Exception::class)
    fun add(x: ByteArray): Boolean {
        return if (queue!!.size < size) {
            synchronized(queue!!) { queue!!.add(x) }

            //            Log.d("Tag::","Size of Stream:"+this.queue.size());
            //            Log.d("Tag::","Original Data:"+ Arrays.toString(x));


            //            System.exit(0);
            true
        } else {
            throw Exception("Buffer Full")
            //            this.remove();
            //            this.add(x);
        }
    }

    fun remove(): ByteArray? {
        return if (queue!!.size != 0) {
            synchronized(queue!!) { return queue!!.removeAt(0) }

            //            throw new Exception("Buffer Empty! Nothing to return!!!");
        } else {
            null
        }
        //        Log.d("t","Removed");
    }

    fun clear() {
        Log.d("Tag::", "Queue Cleared")
        queue = null
    }

    fun getSize(): Int {
        return queue!!.size
    }

    init {
        queue = Collections.synchronizedList(LinkedList())
        this.size = size
    }
}
