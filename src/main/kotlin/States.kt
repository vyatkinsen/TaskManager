enum class States {
    RUNNING {
        override fun nextStates(): Set<States> {
            return setOf(SUSPENDED, READY, WAITING)
        }
    },
    SUSPENDED {
        override fun nextStates(): Set<States> {
            return setOf(READY)
        }
    },
    READY {
        override fun nextStates(): Set<States> {
            return setOf(RUNNING)
        }
    },
    WAITING {
        override fun nextStates(): Set<States> {
            return setOf(READY)
        }
    };

    open fun nextStates(): Set<States> {
        return setOf()
    }
}
