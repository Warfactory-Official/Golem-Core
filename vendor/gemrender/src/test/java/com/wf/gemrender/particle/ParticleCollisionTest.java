package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticleCollisionTest {
	private static final float GRAVITY = 20.0f;

	private final Vector3f scratch = new Vector3f();

	/** A floor: any segment crossing {@code y} on the way down lands on it. */
	private static ParticleCollision.Probe floorAt(double y) {
		return (fx, fy, fz, tx, ty, tz, into) -> {
			if (fy < y || ty >= y) {
				return false;
			}
			into.fraction = (float) ((fy - y) / (fy - ty));
			into.normal = ParticleCollision.PLUS_Y;
			return true;
		};
	}

	/** A wall facing -X: any segment crossing {@code x} left to right runs into it. */
	private static ParticleCollision.Probe wallAt(double x) {
		return (fx, fy, fz, tx, ty, tz, into) -> {
			if (fx > x || tx <= x) {
				return false;
			}
			into.fraction = (float) ((x - fx) / (tx - fx));
			into.normal = ParticleCollision.MINUS_X;
			return true;
		};
	}

	private static ParticleCollision.Probe everything() {
		return (fx, fy, fz, tx, ty, tz, into) -> {
			into.fraction = 0.5f;
			into.normal = ParticleCollision.PLUS_Y;
			return true;
		};
	}

	/** A wall facing -X at {@code wallX}, with a floor under it at {@code floorY}. */
	private static ParticleCollision.Probe roomWithAWall(double wallX, double floorY) {
		ParticleCollision.Probe wall = wallAt(wallX);
		ParticleCollision.Probe floor = floorAt(floorY);
		return (fx, fy, fz, tx, ty, tz, into) -> wall.probe(fx, fy, fz, tx, ty, tz, into)
				|| floor.probe(fx, fy, fz, tx, ty, tz, into);
	}

	private static ParticleStyle stopping() {
		return ParticleStyle.builder()
				.gravity(GRAVITY)
				.stopsOnContact()
				.build();
	}

	@Test
	@DisplayName("a style with no contact response ignores a world that is entirely solid")
	void noResponseNeverContacts() {
		ParticleStyle style = ParticleStyle.builder()
				.gravity(GRAVITY)
				.build();

		ParticleCollision.Contact contact = ParticleCollision.predict(everything(), style,
				0.0, 10.0, 0.0, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f);

		assertThat(contact.hits()).isFalse();
		assertThat(contact.contactAge()).isEqualTo(ParticleCollision.NEVER);
		assertThat(contact.life()).isEqualTo(2.0f);
	}

	@Test
	@DisplayName("a particle dropped onto a floor contacts it at the time the closed form says it does")
	void dropContactsAtTheAnalyticTime() {
		// y(t) = 10 - g t^2 / 2, so a floor at 0 is reached at sqrt(2 * 10 / 20) = 1s exactly.
		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), stopping(),
				0.0, 10.0, 0.0, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f);

		assertThat(contact.hits()).isTrue();
		assertThat(contact.contactAge()).isCloseTo(1.0f, within(0.01f));
		assertThat(contact.normal()).isEqualTo(ParticleCollision.PLUS_Y);
	}

	@Test
	@DisplayName("a particle that stops holds the contact point for the rest of its life")
	void stoppedParticleHoldsItsContactPoint() {
		ParticleStyle style = stopping();
		Vector3f spawn = new Vector3f(0.0f, 10.0f, 0.0f);
		Vector3f velocity = new Vector3f(2.0f, 0.0f, 0.0f);

		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style,
				spawn.x, spawn.y, spawn.z, velocity.x, velocity.y, velocity.z, 4.0f, 0.0f);

		assertThat(contact.restAge()).isEqualTo(contact.contactAge());

		Vector3f resting = ParticleCollision.positionAt(style, spawn, velocity, contact,
				contact.contactAge(), new Vector3f());
		assertThat(resting.y).as("resting on the floor")
				.isCloseTo(0.0f, within(0.05f));

		for (float age = contact.contactAge(); age <= 4.0f; age += 0.25f) {
			Vector3f at = ParticleCollision.positionAt(style, spawn, velocity, contact, age, scratch);
			assertThat(at.x).as("x at t=%.2f", age)
					.isCloseTo(resting.x, within(1e-4f));
			assertThat(at.y).as("y at t=%.2f", age)
					.isCloseTo(resting.y, within(1e-4f));
		}
	}

	@Test
	@DisplayName("a particle that stops keeps the velocity it landed with, so a mesh keeps its attitude")
	void stoppedParticleKeepsItsLandingVelocity() {
		ParticleStyle style = stopping();
		Vector3f velocity = new Vector3f(3.0f, 0.0f, 0.0f);

		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style,
				0.0, 10.0, 0.0, velocity.x, velocity.y, velocity.z, 4.0f, 0.0f);

		Vector3f landing = ParticleCollision.velocityAt(style, velocity, contact, contact.contactAge(),
				new Vector3f());
		Vector3f later = ParticleCollision.velocityAt(style, velocity, contact, 3.5f, scratch);

		assertThat(landing.length()).isGreaterThan(1.0f);
		assertThat(later.x).isCloseTo(landing.x, within(1e-4f));
		assertThat(later.y).isCloseTo(landing.y, within(1e-4f));
	}

	@Test
	@DisplayName("a radius stops the particle's centre short of the surface instead of in it")
	void radiusStopsShortOfTheSurface() {
		ParticleStyle style = stopping();
		Vector3f spawn = new Vector3f(0.0f, 10.0f, 0.0f);
		Vector3f velocity = new Vector3f();

		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style,
				spawn.x, spawn.y, spawn.z, 0.0f, 0.0f, 0.0f, 2.0f, 0.5f);

		Vector3f resting = ParticleCollision.positionAt(style, spawn, velocity, contact,
				contact.contactAge(), scratch);
		assertThat(resting.y).isCloseTo(0.5f, within(0.1f));
	}

	@Test
	@DisplayName("a rebound reverses what went into the surface and taxes what ran along it")
	void reboundSplitsTheVelocity() {
		Vector3f rebound = ParticleCollision.reflect(new Vector3f(4.0f, -6.0f, 0.0f),
				ParticleCollision.PLUS_Y, 0.5f, 0.8f, new Vector3f());

		assertThat(rebound.y).isCloseTo(3.0f, within(1e-5f));
		assertThat(rebound.x).isCloseTo(3.2f, within(1e-5f));
	}

	@Test
	@DisplayName("a velocity already leaving a surface is slowed but never flipped back into it")
	void reboundDoesNotFlipAnOutwardVelocity() {
		Vector3f rebound = ParticleCollision.reflect(new Vector3f(0.0f, 5.0f, 0.0f),
				ParticleCollision.PLUS_Y, 0.5f, 1.0f, new Vector3f());

		assertThat(rebound.y).isEqualTo(5.0f);
	}

	@Test
	@DisplayName("a bouncing particle leaves the floor again and settles at the second contact")
	void bounceLeavesTheFloorAndSettles() {
		ParticleStyle style = ParticleStyle.builder()
				.gravity(GRAVITY)
				.bouncesOnContact(0.5f, 0.8f)
				.build();

		Vector3f spawn = new Vector3f(0.0f, 10.0f, 0.0f);
		Vector3f velocity = new Vector3f(2.0f, 0.0f, 0.0f);

		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style,
				spawn.x, spawn.y, spawn.z, velocity.x, velocity.y, velocity.z, 6.0f, 0.0f);

		assertThat(contact.restAge()).isGreaterThan(contact.contactAge());

		Vector3f airborne = ParticleCollision.positionAt(style, spawn, velocity, contact,
				contact.contactAge() + 0.2f, new Vector3f());
		assertThat(airborne.y).as("back off the floor after the rebound")
				.isGreaterThan(0.2f);

		Vector3f settled = ParticleCollision.positionAt(style, spawn, velocity, contact,
				contact.restAge(), new Vector3f());
		Vector3f later = ParticleCollision.positionAt(style, spawn, velocity, contact, 6.0f, scratch);

		assertThat(settled.y).isCloseTo(0.0f, within(0.15f));
		assertThat(later.x).isCloseTo(settled.x, within(1e-4f));
		assertThat(later.y).isCloseTo(settled.y, within(1e-4f));
	}

	@Test
	@DisplayName("a particle that dies on contact gets a shorter life and nothing else")
	void dieShortensTheLife() {
		ParticleStyle style = ParticleStyle.builder()
				.gravity(GRAVITY)
				.diesOnContact()
				.build();

		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style,
				0.0, 10.0, 0.0, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f);

		assertThat(contact.life()).isCloseTo(1.0f, within(0.01f));
		assertThat(contact.contactAge()).isEqualTo(ParticleCollision.NEVER);
	}

	@Test
	@DisplayName("a wall on the horizontal is found the same way a floor is")
	void horizontalTravelFindsAWall() {
		ParticleStyle style = ParticleStyle.builder()
				.stopsOnContact()
				.build();

		ParticleCollision.Contact contact = ParticleCollision.predict(wallAt(6.0), style,
				0.0, 10.0, 0.0, 3.0f, 0.0f, 0.0f, 5.0f, 0.0f);

		assertThat(contact.contactAge()).isCloseTo(2.0f, within(0.01f));
		assertThat(contact.normal()).isEqualTo(ParticleCollision.MINUS_X);
	}

	@Test
	@DisplayName("a particle whose whole life stays clear of the floor reports no contact")
	void shortFlightMissesTheFloor() {
		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), stopping(),
				0.0, 100.0, 0.0, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

		assertThat(contact.hits()).isFalse();
	}

	/**
	 * A drifting, long-lived particle: ash, whose whole life is a minute of settling. Sized from the speed it
	 * is born at and then capped, the sweep gives a sixty-second flight chords tens of blocks long — and a
	 * chord stands in for the arc as if it were crossed at a constant speed, which over that distance it is
	 * nowhere near. The contact then lands at a fraction of the age it should, and the shader freezes the
	 * particle where the closed form really is at that age: in mid-air, well above the floor it was told it
	 * had reached.
	 */
	@Test
	@DisplayName("a particle that lives a minute still lands on the floor, not above it")
	void longFlightContactsWhereItActuallyLands() {
		// Ash: a gentle pull, a light vertical drag, and a terminal speed it reaches in a couple of seconds.
		ParticleStyle drifting = ParticleStyle.builder()
				.gravity(ParticleStyle.gravityFromPerTickDelta(-0.01f))
				.drag(ParticleStyle.dragFromPerTickFactor(0.95f), ParticleStyle.dragFromPerTickFactor(0.99f))
				.stopsOnContact()
				.build();

		float life = 60.0f;
		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), drifting,
				0.0, 3.0, 0.0, 0.0f, 0.0f, 0.0f, life, 0.0f);

		assertThat(contact.hits()).isTrue();

		// Wherever it says it touched down, that is where the shader will park it, so that point has to be
		// on the floor.
		ParticleCollision.positionAt(drifting, new Vector3f(0.0f, 3.0f, 0.0f), new Vector3f(),
				contact, contact.restAge(), scratch);
		assertThat(scratch.y).isCloseTo(0.0f, within(0.15f));
	}

	/**
	 * Stopping dead is only physical on something that can hold the particle up. A chunk of rubble that
	 * clips a wall has not landed on it (it has lost everything to it and is now falling), and settling
	 * it there leaves it hanging in mid-air against the face for the rest of its life. That is the one
	 * failure of this whole mechanism a player reliably notices, because a blast next to a building glues
	 * a dozen chunks to the wall at head height.
	 */
	@Test
	@DisplayName("rubble that stops against a wall still ends up on the floor under it")
	void stoppingAgainstAWallStillLands() {
		ParticleStyle style = stopping();
		Vector3f spawn = new Vector3f(0.0f, 100.0f, 0.0f);
		Vector3f velocity = new Vector3f(30.0f, 0.0f, 0.0f);

		ParticleCollision.Contact contact = ParticleCollision.predict(roomWithAWall(5.0, 0.0), style,
				spawn.x, spawn.y, spawn.z, velocity.x, velocity.y, velocity.z, 10.0f, 0.0f);

		assertThat(contact.normal()).isEqualTo(ParticleCollision.MINUS_X);
		// The wall is where it stops, not where it settles.
		assertThat(contact.restAge()).isGreaterThan(contact.contactAge());

		ParticleCollision.positionAt(style, spawn, velocity, contact, contact.restAge(), scratch);
		assertThat(scratch.y).isCloseTo(0.0f, within(0.2f));
		assertThat(scratch.x).isCloseTo(5.0f, within(0.01f));
	}

	@Test
	@DisplayName("a mesh particle drifting into a wall stops its side there, not its centre")
	void radiusKeepsTheSideOutOfAWall() {
		ParticleStyle style = stopping();
		Vector3f spawn = new Vector3f(0.0f, 10.0f, 0.0f);
		Vector3f velocity = new Vector3f(2.0f, 0.0f, 0.0f);

		// Centre alone lands at x = 2 (1 s fall), never reaching the wall at 2.5; its side would be inside.
		ParticleCollision.Contact contact = ParticleCollision.predict(roomWithAWall(2.5, 0.0), style,
				spawn.x, spawn.y, spawn.z, velocity.x, velocity.y, velocity.z, 10.0f, 1.0f);

		assertThat(contact.normal()).isEqualTo(ParticleCollision.MINUS_X);
		ParticleCollision.positionAt(style, spawn, velocity, contact, contact.restAge(), scratch);
		assertThat(scratch.x).isCloseTo(1.5f, within(0.01f));
		assertThat(scratch.y).isCloseTo(1.0f, within(0.2f));
	}

	@Test
	@DisplayName("a mesh particle spawned against a wall still flies")
	void radiusOffsetInsideAWallAtSpawnIsIgnored() {
		ParticleStyle style = stopping();

		ParticleCollision.Probe solid = (fx, fy, fz, tx, ty, tz, into) -> {
			if (fx < 0.5 && tx < 0.5) {
				return false;
			}
			into.fraction = fx >= 0.5 ? 0.0f : (float) ((0.5 - fx) / (tx - fx));
			into.normal = ParticleCollision.MINUS_X;
			return true;
		};
		ParticleCollision.Probe floor = floorAt(0.0);
		ParticleCollision.Contact contact = ParticleCollision.predict(
				(fx, fy, fz, tx, ty, tz, into) -> solid.probe(fx, fy, fz, tx, ty, tz, into)
						|| floor.probe(fx, fy, fz, tx, ty, tz, into),
				style, 0.0, 10.0, 0.0, -10.0f, 0.0f, 0.0f, 10.0f, 1.0f);

		assertThat(contact.normal()).isEqualTo(ParticleCollision.PLUS_Y);
		assertThat(contact.contactAge()).isGreaterThan(0.5f);
	}

	@Test
	@DisplayName("a particle that lands on a floor settles on it the moment it arrives")
	void stoppingOnAFloorSettlesThere() {
		ParticleStyle style = stopping();

		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style,
				0.0, 10.0, 0.0, 0.0f, 0.0f, 0.0f, 10.0f, 0.0f);

		assertThat(contact.normal()).isEqualTo(ParticleCollision.PLUS_Y);
		assertThat(contact.restAge()).isEqualTo(contact.contactAge());
	}

	@Test
	@DisplayName("a long flight is still swept finely where it bends")
	void longFlightIsNotOneCoarseChord() {
		ParticleStyle drifting = ParticleStyle.builder()
				.gravity(ParticleStyle.gravityFromPerTickDelta(-0.01f))
				.drag(ParticleStyle.dragFromPerTickFactor(0.95f), ParticleStyle.dragFromPerTickFactor(0.99f))
				.stopsOnContact()
				.build();

		// Far enough down that the particle is at terminal velocity long before it arrives, which is the
		// half of the flight a single chord may legitimately cover.
		ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(-400.0), drifting,
				0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f, 60.0f, 0.0f);

		assertThat(contact.hits()).isTrue();
		ParticleCollision.positionAt(drifting, new Vector3f(), new Vector3f(), contact, contact.restAge(),
				scratch);
		assertThat(scratch.y).isCloseTo(-400.0f, within(0.15f));
	}
}
