package com.wf.gemrender.particle;


public enum ContactResponse {
    /**
     * Fly through everything, which is what a particle did before there was a choice. No sweep is done at
     * all, so a style that does not want contact pays nothing for the feature existing.
     */
    NONE,

    /**
     * Stop dead where it landed and stay there for the rest of its life, keeping the attitude it landed
     * with. Rubble, casings, gore: the things whose whole point is that they end up on the floor.
     *
     * <p>Landed means a floor. A surface that cannot hold the particle up (a wall, a ceiling) takes
     * everything it had and then lets it fall, and it settles on whatever is below that instead. Stopping
     * dead on a vertical face would leave it hanging against the wall in mid-air.
     */
    STOP,

    /**
     * Rebound once, then settle where the rebound lands. One bounce rather than an arbitrary number because
     * each one is a separate segment to predict and to store; past the first, nobody is counting.
     */
    BOUNCE,

    /**
     * Vanish on contact. Spray and sparks that should not survive the wall they hit.
     *
     * <p>Implemented by shortening the particle's life at spawn rather than by anything the shader does, so
     * it is free: a particle that dies at the wall is just a particle with a shorter life.
     */
    DIE
}
