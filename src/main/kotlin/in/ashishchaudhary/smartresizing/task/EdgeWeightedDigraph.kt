package `in`.ashishchaudhary.smartresizing.task

import java.util.LinkedList

class EdgeWeightedDigraph(private val V: Int) {
    val adj = Array(V) { LinkedList<DirectedEdge>() }

    fun addEdge(e: DirectedEdge) {
        val v = e.from()
        adj[v].push(e)
    }

    fun adj(v: Int): Iterable<DirectedEdge> = adj[v]
    fun V() = V
    fun E() = adj.fold(0) { acc, queue -> acc + queue.size }
    fun edges(): Iterable<DirectedEdge> {
        val queue = LinkedList<DirectedEdge>()
        adj.asSequence().flatMap { it.asSequence() }.forEach { queue.push(it) }
        return queue
    }
}