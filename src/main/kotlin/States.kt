enum class States {
    RUNNING {
        override fun nextStates(): Set<States> {
            return setOf(SUSPENDED, READY, WAIT)
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
    WAIT {
        override fun nextStates(): Set<States> {
            return setOf(READY)
        }
    };

    open fun nextStates(): Set<States> {
        return setOf()
    }
}
