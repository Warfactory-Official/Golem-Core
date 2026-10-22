package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticleShapesTest {
	private static final float GRAVITY = 20.0f;

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

	@Test
	@DisplayName("a decal lies in the plane of its normal, lifted just off it, for any normal and roll")
	void decalLiesOnItsPlane() {
		Vector3f position = new Vector3f(3.0f, 4.0f, 5.0f);
		Vector3f[] normals = {new Vector3f(0, 1, 0), new Vector3f(0, -1, 0), new Vector3f(1, 0, 0),
				new Vector3f(0, 0, -1), new Vector3f(0.3f, 0.8f, -0.5f).normalize()};
		for (Vector3f n : normals) {
			for (float roll = 0.0f; roll < 6.3f; roll += 0.7f) {
				Vector3f a = ParticleShapes.decalCorner(position, n, roll, -0.5f, -0.5f, 0.2f, new Vector3f());
				Vector3f b = ParticleShapes.decalCorner(position, n, roll, 0.5f, 0.5f, 0.2f, new Vector3f());
				for (Vector3f corner : new Vector3f[]{a, b}) {
					float height = new Vector3f(corner).sub(position).dot(n);
					assertThat(height).as("height off the plane, n=%s", n)
							.isCloseTo(ParticleShapes.DECAL_LIFT, within(1e-5f));
				}
				assertThat(a.distance(b)).as("diagonal").isCloseTo(0.2f * (float) Math.sqrt(2.0), within(1e-5f));
			}
		}
	}

	@Test
	@DisplayName("a decal's quad faces out of the surface: t x b == n, so back-face culling keeps the front")
	void decalBasisIsRightHanded() {
		Vector3f n = new Vector3f(-0.6f, 0.0f, 0.8f);
		Matrix3f basis = ParticleShapes.planeBasis(n, 1.1f, new Matrix3f());
		Vector3f t = basis.getColumn(0, new Vector3f());
		Vector3f b = basis.getColumn(1, new Vector3f());
		Vector3f cross = t.cross(b, new Vector3f());
		assertThat(cross.distance(n)).isLessThan(1e-5f);
	}

	@Test
	@DisplayName("a streak is as long as the distance covered in its streak time, never shorter than wide")
	void streakLength() {
		assertThat(ParticleShapes.streakLength(new Vector3f(30, 40, 0), 0.05f, 0.02f)).isCloseTo(1.0f,
				within(1e-5f));
		assertThat(ParticleShapes.streakLength(new Vector3f(0, 0.1f, 0), 0.05f, 0.02f)).isEqualTo(0.05f);
	}

	@Test
	@DisplayName("a tumbling body comes to rest lying down: its long axis horizontal")
	void bodyRestsLyingDown() {
		ParticleStyle style = ParticleStyle.builder()
				.gravity(GRAVITY)
				.spin(23.0f)
				.bouncesOnContact(0.3f, 0.6f)
				.build();
		Vector3f velocity = new Vector3f(2.0f, 3.0f, -1.0f);
		for (float phase = 0.0f; phase < 6.3f; phase += 0.37f) {
			ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style, 0.0, 1.5, 0.0,
					velocity.x, velocity.y, velocity.z, 5.0f, 0.02f);
			assertThat(contact.restAge()).as("fixture lands").isLessThan(5.0f);

			Matrix3f atRest = ParticleShapes.bodyBasis(style, velocity, phase, contact, contact.restAge() + 0.5f,
					new Matrix3f());
			Vector3f y = atRest.getColumn(1, new Vector3f());
			assertThat(y.y).as("vertical part of +Y at rest, phase %s", phase).isCloseTo(0.0f, within(1e-4f));

			Matrix3f later = ParticleShapes.bodyBasis(style, velocity, phase, contact, contact.restAge() + 2.0f,
					new Matrix3f());
			assertThat(later.equals(atRest, 1e-6f)).as("holds still once at rest").isTrue();
		}
	}

	@Test
	@DisplayName("a body tumbles in flight: its attitude turns at the spin rate about a horizontal axis")
	void bodyTumblesInFlight() {
		ParticleStyle style = ParticleStyle.builder()
				.gravity(GRAVITY)
				.spin(10.0f)
				.build();
		Vector3f velocity = new Vector3f(1.0f, 2.0f, 0.0f);
		ParticleCollision.Contact free = ParticleCollision.none(3.0f);
		Vector3f y0 = ParticleShapes.bodyBasis(style, velocity, 0.0f, free, 0.0f, new Matrix3f())
				.getColumn(1, new Vector3f());
		Vector3f y1 = ParticleShapes.bodyBasis(style, velocity, 0.0f, free, 0.1f, new Matrix3f())
				.getColumn(1, new Vector3f());
		assertThat(y0.angle(y1)).isCloseTo(1.0f, within(1e-4f));
		Vector3f axis = ParticleShapes.bodyBasis(style, velocity, 0.0f, free, 0.1f, new Matrix3f())
				.getColumn(0, new Vector3f());
		assertThat(axis.y).isZero();
	}

	@Test
	@DisplayName("a body shrinks through the fade window; fadeOut 0 is the whole life, as for alpha")
	void bodyScale() {
		ParticleStyle held = ParticleStyle.builder()
				.fadeOut(0.5f)
				.build();
		assertThat(ParticleShapes.bodyScale(held, 0.4f)).isEqualTo(1.0f);
		assertThat(ParticleShapes.bodyScale(held, 0.75f)).isCloseTo(0.5f, within(1e-6f));
		ParticleStyle whole = ParticleStyle.builder()
				.build();
		assertThat(ParticleShapes.bodyScale(whole, 0.9f)).isCloseTo(0.1f, within(1e-6f));
		assertThat(1.0f - ParticleShapes.bodyScale(whole, 0.9f)).isCloseTo(1.0f - ParticleMotion.alpha(whole, 0.9f),
				within(1e-6f));
	}

	@Test
	@DisplayName("a streak that has settled, bounced or stopped, collapses to a width x width dot")
	void settledStreakIsADot() {
		float width = 0.035f;
		Vector3f eye = new Vector3f(4.0f, 3.0f, -6.0f);
		Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f);
		ParticleStyle bounce = ParticleStyle.builder()
				.gravity(GRAVITY)
				.streak(0.05f)
				.bouncesOnContact(0.45f, 0.6f)
				.build();
		ParticleStyle stop = ParticleStyle.builder()
				.gravity(GRAVITY)
				.streak(0.05f)
				.stopsOnContact()
				.build();
		Vector3f spawn = new Vector3f(0.0f, 1.5f, 0.0f);
		Vector3f velocity = new Vector3f(5.0f, 2.0f, -3.0f);
		for (ParticleStyle style : new ParticleStyle[]{bounce, stop}) {
			ParticleCollision.Contact contact = ParticleCollision.predict(floorAt(0.0), style, spawn.x, spawn.y,
					spawn.z, velocity.x, velocity.y, velocity.z, 5.0f, 0.0f);
			assertThat(contact.restAge()).as("fixture settles").isLessThan(5.0f);

			Vector3f flying = ParticleCollision.motionAt(style, velocity, contact, contact.restAge() * 0.5f,
					new Vector3f());
			assertThat(ParticleShapes.streakLength(flying, width, style.streak)).as("in flight").isGreaterThan(width);

			for (float after : new float[]{0.0f, 0.3f, 2.0f}) {
				float age = contact.restAge() + after;
				Vector3f at = ParticleCollision.positionAt(style, spawn, velocity, contact, age, new Vector3f());
				Vector3f v = ParticleCollision.motionAt(style, velocity, contact, age, new Vector3f());
				Vector3f head = ParticleShapes.streakCorner(at, v, eye, right, 0.0f, 0.5f, width, style.streak,
						new Vector3f());
				Vector3f tail = ParticleShapes.streakCorner(at, v, eye, right, 0.0f, -0.5f, width, style.streak,
						new Vector3f());
				assertThat(head.distance(tail)).as("settled length, %s after rest", after)
						.isCloseTo(width, within(1e-6f));
			}
		}
	}
}
