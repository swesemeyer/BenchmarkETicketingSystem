/**
 *
 */
package uk.ac.surrey.bets_framework.protocol.ppetsabc;

import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

import org.junit.Before;
import org.junit.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.plaf.jpbc.field.curve.CurveElement;
import uk.ac.surrey.bets_framework.Crypto;
import uk.ac.surrey.bets_framework.Crypto.BigIntEuclidean;
import uk.ac.surrey.bets_framework.protocol.data.Data;
import uk.ac.surrey.bets_framework.protocol.data.ListData;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.PPETSABCSharedMemory;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.PPETSABCSharedMemory.Actor;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.PPETSABCSharedMemory.PairingType;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.data.CentralAuthorityData;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.data.SellerData;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.data.UserData;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.data.ValidatorData;

/**
 * @author swesemeyer
 *
 */
public class TestPPETSABC_Type_X_Pairing {

	/** Logback logger. */
	private static final Logger LOG = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
			.getLogger(TestPPETSABC_Type_X_Pairing.class);

	private static final Logger LOG_TIME = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("timings");

	Encoder base64 = Base64.getEncoder();
	Crypto crypto;
	PPETSABCSharedMemory sharedMemory = null;

	// how many times shall we execute this test?
	// this helps to average the execution time in a more precise manner
	int num_of_iterations = 20;
	// sorted map to store the different timings

	SortedMap<String, LongAdder> sortedTimings = new TreeMap<>();

	@Before
	public void setUp() throws Exception {
		// set the desired log level
		LOG.setLevel(Level.DEBUG);
	}

	
	
	private void initSystem() {
		LOG.debug("Starting Initialisation");
		long setup_time = Instant.now().toEpochMilli();
		crypto = Crypto.getInstance();

		sharedMemory = new PPETSABCSharedMemory();
		// change the Pairing Type to the curve you want
		sharedMemory.setPairingType(PairingType.TYPE_A);
		sharedMemory.skipVerification = false;
		sharedMemory.rBits = 160; //note for type A1 pairings this represents the number of primes!
		sharedMemory.qBits = 512;
		sharedMemory.clearTest();
		setup_time = Instant.now().toEpochMilli() - setup_time;
		LOG.info("Initialising system (Server) took (ms): " + setup_time);
		LOG.debug("Setting up Seller:");
		sharedMemory.actAs(Actor.SELLER);
		final BigInteger x_s = crypto.secureRandom(sharedMemory.p);
		final SellerData sellerData = (SellerData) sharedMemory.getData(Actor.SELLER);
		sellerData.x_s = x_s;
		LOG.debug("Seller x_s:" + sellerData.x_s);
		LOG.debug("Initialisation complete:");
	}
	private void addTimings(String timingsName, long timings) {
		if (sortedTimings.containsKey(timingsName)) {
			sortedTimings.get(timingsName).add(timings);
		} else {
			LongAdder tmp = new LongAdder();
			tmp.add(timings);
			sortedTimings.put(timingsName, tmp);
		}
	}

	@Test

	public void testProtocol() {
		byte[] data;
		boolean success;
		long overall_start;
		long time_start;
		long time_end;
		long durationInMS;

		for (int iter = 1; iter <= num_of_iterations; iter++) {
			LOG.info("***************************************************************************");
			LOG.info("************************* Iteration: " + iter+" *************************");
			LOG.info("***************************************************************************");
			time_start = Instant.now().toEpochMilli();
			overall_start = time_start;
			
			LOG.info("Going through system initialisation states");
			
			initSystem();
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-00000: Initialise System", durationInMS);
			
			// Registration States:
			LOG.info("Going through Registration states");

			// Generate Seller Identify: RState02 (Android)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.SELLER);
			data = this.generateSellerIdentity();
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-00100: Generate Seller Identify", durationInMS);
			LOG_TIME.info("Timing-00100: Generate Seller Identify: RState02 (Android) took (ms): " + durationInMS);

			// Generates the seller's credentials: RState05 (Server)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.CENTRAL_AUTHORITY);
			data = this.generateSellerCredentials(data);
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-00200: Generates the seller's credentials", durationInMS);
			LOG_TIME.info(
					"Timing-00200: Generates the seller's credentials: RState05 (Server) took (ms): " + durationInMS);
			if (data == null) {
				fail("Seller credential creation failed");
			}

			// Verify Seller credentials: RState03 (Android)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.SELLER);
			success = this.verifySellerCredentials(data);
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-00300: Verify Seller credentials", durationInMS);
			LOG_TIME.info("Timing-00300: Verify Seller credentials: RState03 (Android) took (ms): " + durationInMS);
			if (!success) {
				fail("Seller credentials did not validate");
			}

			// Generate the user identity data: RState04 (Android)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.USER);
			data = this.generateUserIdentity();
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-00400: Generate the user identity data", durationInMS);

			LOG_TIME.info(
					"Timing-00400: Generate the user identity data: RState04 (Android) took (ms): " + durationInMS);

			// Generate the user's credentials: RState07 (Server)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.CENTRAL_AUTHORITY);
			data = this.generateUserCredentials(data);
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-00500: Generate the user's credentials", durationInMS);
			LOG_TIME.info(
					"Timing-00500: Generate the user's credentials: RState07 (Server) took (ms): " + durationInMS);
			if (data == null) {
				fail("user credential creation failed");
			}

			// Verify the returned user's credential data:RState05 (Android)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.USER);
			success = this.verifyUserCredentials(data);
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-00600: Verify the returned user's credential data", durationInMS);
			LOG_TIME.info(
					"Timing-00600: Verify the returned user's credential data: RState05 took (ms): " + durationInMS);
			if (!success) {
				fail("User credentials did not validate");
			}

			LOG.info("Going through Issuing states");

			// Issuing States:

			// Generate the seller's proof: IState08 (Server)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.SELLER);
			data = this.generateSellerProof();
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-00700: Generate the seller's proof", durationInMS);
			LOG_TIME.info("Timing-00700: Generate the seller's proof: IState08 (Server) took (ms): " + durationInMS);
			if (data == null) {
				fail("Seller proof generation failed");
			}

			// Verify the seller's proof: IState06 (Android)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.SELLER);
			success = this.verifySellerProof(data);
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-00800: Verify the seller's proof", durationInMS);
			LOG_TIME.info("Timing-00800: Verify the seller's proof: IState06 (Android) took (ms): " + durationInMS);
			if (!success) {
				fail("Seller proof verification failed");
			}

			// Generate the user proof data: IState06 (Android)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.USER);
			data = this.generateUserProof();
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-00900: Generate the user proof", durationInMS);
			LOG_TIME.info("Timing-00900: Generate the user proof: IState06 (Android) took (ms): " + durationInMS);

			// Verify the user proof: IState10 (Server)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.SELLER);
			success = this.verifyUserProof(data);
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-01000: Verify the user proof", durationInMS);
			LOG_TIME.info("Timing-01000: Verify the user proof: IState10 (Server) took (ms): " + durationInMS);
			if (!success) {
				fail("user proof verification failed");
			}

			// Generate ticket serial number: IState10 (Server)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.SELLER);
			data = this.generateTicketSerialNumber();
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-01100: Generate ticket serial number", durationInMS);
			LOG_TIME.info("Timing-01100: Generate ticket serial number: IState10 (Server) took (ms): " + durationInMS);

			// Verify the returned ticket serial number data: IState08 (Android)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.USER);
			success = this.verifyTicketSerialNumber(data);
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-01200: Verify the returned ticket serial number data", durationInMS);
			LOG_TIME.info("Timing-01200: Verify the returned ticket serial number data: IState08 (Android) took (ms): "
					+ durationInMS);
			if (!success) {
				fail("ticket serial number verification failed");
			}

			LOG.info("Going through Validation states");

			// Generate the validator's random number: VState1 (Server)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.VALIDATOR);
			data = this.generateValidatorRandomNumber();
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-01300: Generate the validator's random number", durationInMS);
			LOG_TIME.info("Timing-01300: Generate the validator's random number: VState1 (Server) took (ms): "
					+ durationInMS);

			// Generate the ticket transcript data: VState09 (Android)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.USER);
			data = this.generateTicketTranscript(data);
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-01400: Generate the ticket transcript data", durationInMS);
			LOG_TIME.info(
					"Timing-01400: Generate the ticket transcript data: VState09 (Android) took (ms): " + durationInMS);

			// Verifies the ticket proof: VState13 (Server)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.VALIDATOR);
			success = this.verifyTicketProof(data);
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-01500: Verifies the ticket proof", durationInMS);
			LOG_TIME.info("Timing-01500: Verifies the ticket proof: VState13 (Server) took (ms): " + durationInMS);
			if (!success) {
				fail("ticket proof verification failed");
			}

			// Detect if the ticket has been double spent: VState13(Server)
			time_start = Instant.now().toEpochMilli();
			sharedMemory.actAs(Actor.VALIDATOR);
			success = !this.detectDoubleSpend();
			time_end = Instant.now().toEpochMilli();
			durationInMS = time_end - time_start;
			addTimings("Timing-01600: Verifies the ticket proof", durationInMS);
			LOG_TIME.info("Timing-01600: Detect if the ticket has been double spent: VState13(Server) took (ms): "
					+ durationInMS);
			if (!success) {
				fail("ticket double spend check failed");
			}
			durationInMS = time_end - overall_start;
			addTimings("Timing-99999: Overall run", durationInMS);
			LOG_TIME.info("Timing-99999: Overall run: Total run of the protocol with no comms overhead took (ms): "
					+ durationInMS);
		}
		Iterator<String> iter = sortedTimings.keySet().iterator();
		while (iter.hasNext()) {
			String key = iter.next();
			LOG.debug("Average of " + key + " after " + num_of_iterations + " iterations is: "
					+ (sortedTimings.get(key).sum() / (double) num_of_iterations));
		}
	}

	private byte[] generateSellerIdentity() {

		final SellerData sellerData = (SellerData) sharedMemory.getData(Actor.SELLER);
		final Crypto crypto = Crypto.getInstance();

		final CurveElement<?, ?> rho = sharedMemory.rho;

		// Calculate Y_I. Note that x_s has already been obtained.
		sellerData.Y_S = rho.mul(sellerData.x_s).getImmutable();

		// Compute proof PI_1_S = (c, s, M_1_S, Y_I):
		final BigInteger t_s = crypto.secureRandom(sharedMemory.p);
		final Element M_1_S = sharedMemory.pairing.getG1().newRandomElement().getImmutable();
		// LOG.debug("M_1_S: "+M_1_S);

		// LOG.debug("Y_S: "+sellerData.Y_S);
		final CurveElement<?, ?> T_s = rho.mul(t_s);
		final ListData cData = new ListData(Arrays.asList(M_1_S.toBytes(), sellerData.Y_S.toBytes(), T_s.toBytes()));
		final byte[] c = crypto.getHash(cData.toBytes());
		final BigInteger cNum = (new BigInteger(1, c)).mod(sharedMemory.p);
		// LOG.debug("cNum(no mod p): "+new BigInteger(1, c));
		// LOG.debug("cNum(mod p): "+cNum);

		final BigInteger s = (t_s.subtract(cNum.multiply(sellerData.x_s))).mod(sharedMemory.p);

		// Send ID_I, PI_1_S (which includes Y_I) and VP_S
		final ListData sendData = new ListData(Arrays.asList(SellerData.ID_S, M_1_S.toBytes(), sellerData.Y_S.toBytes(),
				c, s.toByteArray(), sharedMemory.stringToBytes(sellerData.VP_S)));

		return sendData.toBytes();
	}

	/**
	 * Detects if the ticket has been double spent.
	 *
	 * @return True if the ticket is double spent.
	 */
	private boolean detectDoubleSpend() {
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();
		final ValidatorData validatorData = (ValidatorData) sharedMemory.getData(Actor.VALIDATOR);

		// Here we do not (and cannot since it requires the private x_u)
		// E^r_dash/E_dash^r and Y_U, as we just compare the stored
		// ticket transcript.
		return validatorData.D.isEqual(validatorData.D_last) && !validatorData.E.isEqual(validatorData.E_last);
	}

	/**
	 * Generates the seller's credentials.
	 *
	 * @param data
	 *            The data received from the seller.
	 * @return The seller's credential data.
	 */
	private byte[] generateSellerCredentials(byte[] data) {

		final Crypto crypto = Crypto.getInstance();
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();

		final CentralAuthorityData centralAuthorityData = (CentralAuthorityData) sharedMemory
				.getData(Actor.CENTRAL_AUTHORITY);

		// Decode the received data.
		final ListData listData = ListData.fromBytes(data);

		if (listData.getList().size() != 6) {
			LOG.error("wrong number of data elements: " + listData.getList().size());
			return null;
		}

		// ID_I not used.
		final Element M_1_S = sharedMemory.curveElementFromBytes(listData.getList().get(1));
		// LOG.debug("M_1_S: "+M_1_S);
		final Element Y_S = sharedMemory.curveElementFromBytes(listData.getList().get(2));
		// LOG.debug("Y_S: "+Y_S);
		final byte[] c = listData.getList().get(3);
		final BigInteger cNum = new BigInteger(1, c).mod(sharedMemory.p);
		// LOG.debug("cNum: "+cNum);
		final BigInteger s = new BigInteger(listData.getList().get(4));
		// How long does the seller want to request credentials for
		final String VP_S = sharedMemory.stringFromBytes(listData.getList().get(5));
		// NB this period might get changed by the CA if it thinks the requested period
		// is not appropriate.

		// Verify PI_1_S via c.
		final Element check = sharedMemory.rho.mul(s).add(Y_S.mul(cNum));
		LOG.debug("check= " + check);
		final ListData cVerifyData = new ListData(Arrays.asList(M_1_S.toBytes(), Y_S.toBytes(), check.toBytes()));
		final byte[] cVerify = crypto.getHash(cVerifyData.toBytes());
		if (!Arrays.equals(c, cVerify)) {
			LOG.error("failed to verify PI_1_S");
			if (!sharedMemory.skipVerification) {
				return null;
			}
		}
		LOG.debug("SUCCESS: passed verification of PI_1_S");
		// Select random c_s and r_s.
		final BigInteger c_s = crypto.secureRandom(sharedMemory.p).mod(sharedMemory.p);
		final BigInteger r_s = crypto.secureRandom(sharedMemory.p).mod(sharedMemory.p);

		// Compute delta_S:
		//
		// delta_s = (g_0 Ys g_frak^r_s) ^ 1/(x+c_s) is equivalent to
		// delta_s = (g_0 + Ys + r_s * g_frak) / (x+c_s).
		//
		// However, we cannot calculate 1 / (x+c_s) directly, but we can use
		// the extended GCD algorithm assuming we are working in a
		// group with order p which is prime.
		//
		// If d = (1/a) * P = k * P for some k in Z_p
		//
		// Since p is prime, the GCD of a and p is 1, and hence we can find
		// m and n such that:
		//
		// a*m + p*n = 1
		//
		// and
		//
		// d = (1/a) * P
		// = (1/a) * 1 * P
		// = (1/a) * (a*m + p*n) * P
		// = (a*m*P/a) + (P*p*n/a)
		// = m*P + k*P*p*n since (1/a) * P = k * P
		// = m*P since p*P = 0
		//
		// Therefore d = (1/a) * P = k * P = m * P and hence k = m
		//
		// We can use the extended Euclidean algorithm to compute:
		//
		// a*m + p*n = gcd(a, p) = 1
		//
		// Since (1/a) * P = m*P, and we have m from above, then we know d.

		final BigIntEuclidean gcd = BigIntEuclidean.calculate(centralAuthorityData.x.add(c_s).mod(sharedMemory.p),
				sharedMemory.p);
		final byte[] vpsHash = crypto.getHash(VP_S.getBytes());
		final BigInteger vpsHashNum = new BigInteger(1, vpsHash).mod(sharedMemory.p);
		LOG.debug("vpsHashNum: " + vpsHashNum);

		final CurveElement<?, ?> delta_S = (CurveElement<?, ?>) sharedMemory.g_n[0]
				.add(sharedMemory.g_n[1].mul(vpsHashNum)).add(Y_S).add(sharedMemory.g_frak.mul(r_s))
				.mul(gcd.x.mod(sharedMemory.p)).getImmutable();

		// Store the seller credentials for later use when we are the
		// seller.
		sharedMemory.actAs(Actor.SELLER);
		final SellerData sellerData = (SellerData) sharedMemory.getData(Actor.SELLER);
		sellerData.Y_S = Y_S;
		sellerData.c_s = c_s;
		sellerData.r_s = r_s;
		sellerData.delta_S = delta_S;
		sellerData.VP_S = VP_S;
		sharedMemory.actAs(Actor.CENTRAL_AUTHORITY);

		// Send c_s, r_s, delta_S, VP_S
		// note that VP_S might have changed if the CA does not like to issue
		// credentials for the period requested
		final ListData sendData = new ListData(Arrays.asList(c_s.toByteArray(), r_s.toByteArray(), delta_S.toBytes(),
				sharedMemory.stringToBytes(VP_S)));
		return sendData.toBytes();
	}

	private byte[] generateSellerProof() {
		// Note that all elliptic curve calculations are in an additive group
		// such that * -> + and ^ -> *.
		final SellerData sellerData = (SellerData) sharedMemory.getData(Actor.SELLER);
		// final Crypto crypto = Crypto.getInstance();

		// Select random z and v.
		final BigInteger z = crypto.secureRandom(sharedMemory.p);
		final BigInteger v = crypto.secureRandom(sharedMemory.p);
		final Element Q = sellerData.delta_S.add(sharedMemory.theta.mul(z)).getImmutable();

		// Compute Z = g^z * theta^v
		final Element Z = sharedMemory.g.mul(z).add(sharedMemory.theta.mul(v)).getImmutable();

		// Compute gamma = g^z_dash * theta^v_dash where z_dash = z*c_s and
		// v_dash = v*c_s (duplicate label of gamma and Z_c_s in
		// paper).
		final BigInteger z_dash = z.multiply(sellerData.c_s);
		final BigInteger v_dash = v.multiply(sellerData.c_s);
		final Element gamma = sharedMemory.g.mul(z_dash).add(sharedMemory.theta.mul(v_dash));

		// Compute the proof PI_2_S = (M_2_S, Q, Z, gamma, Z_dash, gamma_dash,
		// omega, omega_dash, c_bar_1-3, s_bar_1-2, s_hat_1-2,
		// r_bar_1-5)
		final BigInteger z_bar = crypto.secureRandom(sharedMemory.p);
		final BigInteger v_bar = crypto.secureRandom(sharedMemory.p);
		final BigInteger z_hat = crypto.secureRandom(sharedMemory.p);
		final BigInteger v_hat = crypto.secureRandom(sharedMemory.p);
		final BigInteger x_bar_s = crypto.secureRandom(sharedMemory.p);
		final BigInteger v_bar_s = crypto.secureRandom(sharedMemory.p);
		final BigInteger c_bar_s = crypto.secureRandom(sharedMemory.p);
		final Element M_2_S = sharedMemory.pairing.getG1().newRandomElement().getImmutable();

		// Z_dash = g^z_bar * theta^v_bar
		final Element Z_dash = sharedMemory.g.mul(z_bar).add(sharedMemory.theta.mul(v_bar));

		// gamma_dash = g^z_hat * theta^v_hat
		final Element gamma_dash = sharedMemory.g.mul(z_hat).add(sharedMemory.theta.mul(v_hat));

		// omega = e(Q, g_bar) / e(g_0, g) e(g_1,g)^H(VP_S)
		final Element omega_1 = sharedMemory.pairing.pairing(Q, sharedMemory.g_bar).getImmutable();
		final Element omega_2 = sharedMemory.pairing.pairing(sharedMemory.g_n[0], sharedMemory.g).getImmutable();

		final byte[] vpsHash = crypto.getHash(sellerData.VP_S.getBytes());
		final BigInteger vpsHashNum = new BigInteger(1, vpsHash).mod(sharedMemory.p);
		LOG.debug("vpsHashNum: " + vpsHashNum);

		final Element omega_3 = sharedMemory.pairing.pairing(sharedMemory.g_n[1], sharedMemory.g).pow(vpsHashNum);

		final Element omega = omega_1.div((omega_2.mul(omega_3))).getImmutable();

		// omega_dash = e(rho, g)^x_bar_s * e(g_frak, g)^v_bar_s * e(Q,
		// g)^-c_bar_s * e(theta, g)^z_bar * e(theta, g_bar)^z_bar
		final Element omega_dash_1 = sharedMemory.pairing.pairing(sharedMemory.rho, sharedMemory.g).pow(x_bar_s)
				.getImmutable();

		final Element omega_dash_2 = sharedMemory.pairing.pairing(sharedMemory.g_frak, sharedMemory.g).pow(v_bar_s)
				.getImmutable();

		final Element omega_dash_3 = sharedMemory.pairing.pairing(Q, sharedMemory.g)
				.pow(c_bar_s.negate().mod(sharedMemory.p)).getImmutable();

		final Element omega_dash_4 = sharedMemory.pairing.pairing(sharedMemory.theta, sharedMemory.g).pow(z_hat)
				.getImmutable();

		final Element omega_dash_5 = sharedMemory.pairing.pairing(sharedMemory.theta, sharedMemory.g_bar).pow(z_bar)
				.getImmutable();

		final Element omega_dash = omega_dash_1.mul(omega_dash_2).mul(omega_dash_3).mul(omega_dash_4).mul(omega_dash_5)
				.getImmutable();

		// Calculate hashes.
		final ListData c_bar_1Data = new ListData(Arrays.asList(M_2_S.toBytes(), Z.toBytes(), Z_dash.toBytes()));
		final byte[] c_bar_1 = crypto.getHash(c_bar_1Data.toBytes());
		final BigInteger c_bar_1Num = new BigInteger(1, c_bar_1).mod(sharedMemory.p);

		final ListData c_bar_2Data = new ListData(
				Arrays.asList(M_2_S.toBytes(), gamma.toBytes(), gamma_dash.toBytes()));
		final byte[] c_bar_2 = crypto.getHash(c_bar_2Data.toBytes());
		final BigInteger c_bar_2Num = new BigInteger(1, c_bar_2).mod(sharedMemory.p);

		final ListData c_bar_3Data = new ListData(
				Arrays.asList(M_2_S.toBytes(), omega.toBytes(), omega_dash.toBytes()));
		final byte[] c_bar_3 = crypto.getHash(c_bar_3Data.toBytes());
		final BigInteger c_bar_3Num = new BigInteger(1, c_bar_3).mod(sharedMemory.p);

		// Calculate remaining numbers.
		final BigInteger s_bar_1 = z_bar.subtract(c_bar_1Num.multiply(z)).mod(sharedMemory.p);
		final BigInteger s_bar_2 = v_bar.subtract(c_bar_1Num.multiply(v)).mod(sharedMemory.p);

		final BigInteger s_hat_1 = z_hat.subtract(c_bar_2Num.multiply(z_dash)).mod(sharedMemory.p);
		final BigInteger s_hat_2 = v_hat.subtract(c_bar_2Num.multiply(v_dash)).mod(sharedMemory.p);

		final BigInteger r_bar_1 = x_bar_s.subtract(c_bar_3Num.multiply(sellerData.x_s)).mod(sharedMemory.p);
		final BigInteger r_bar_2 = v_bar_s.subtract(c_bar_3Num.multiply(sellerData.r_s)).mod(sharedMemory.p);
		final BigInteger r_bar_3 = c_bar_s.subtract(c_bar_3Num.multiply(sellerData.c_s)).mod(sharedMemory.p);
		final BigInteger r_bar_4 = z_hat.subtract(c_bar_3Num.multiply(z_dash)).mod(sharedMemory.p);
		final BigInteger r_bar_5 = z_bar.subtract(c_bar_3Num.multiply(z)).mod(sharedMemory.p);

		// Send PI_2_S.
		final ListData sendData = new ListData(Arrays.asList(M_2_S.toBytes(), Q.toBytes(), Z.toBytes(), gamma.toBytes(),
				Z_dash.toBytes(), gamma_dash.toBytes(), omega.toBytes(), omega_dash.toBytes(), c_bar_1, c_bar_2,
				c_bar_3, s_bar_1.toByteArray(), s_bar_2.toByteArray(), s_hat_1.toByteArray(), s_hat_2.toByteArray(),
				r_bar_1.toByteArray(), r_bar_2.toByteArray(), r_bar_3.toByteArray(), r_bar_4.toByteArray(),
				r_bar_5.toByteArray()));
		return sendData.toBytes();
	}

	private byte[] generateTicketSerialNumber() {
		// Note that all elliptic curve calculations are in an additive group
		// such that * -> + and ^ -> *.
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();
		final SellerData sellerData = (SellerData) sharedMemory.getData(Actor.SELLER);
		final Crypto crypto = Crypto.getInstance();

		// Select random d_dash and omega_u.
		final BigInteger d_dash = crypto.secureRandom(sharedMemory.p);
		final BigInteger omega_u = crypto.secureRandom(sharedMemory.p);

		// pick a random serial number... Should probably do something slightly more
		// clever here
		final BigInteger s_u = crypto.secureRandom(sharedMemory.p);

		// Compute psi_u = H(P_U || Price || Service || Ticket Valid_Period)
		final ListData psi_uData = new ListData(
				Arrays.asList(sharedMemory.stringToBytes(sellerData.U_membershipDetails), SellerData.TICKET_PRICE,
						SellerData.TICKET_SERVICE, sharedMemory.stringToBytes(sellerData.VP_T)));
		final byte[] psi_u = crypto.getHash(psi_uData.toBytes());
		final BigInteger psi_uNum = new BigInteger(1, psi_u).mod(sharedMemory.p);

		// Compute T_U = (g_0 * Y * g_1^d_dash * g_2^s_u)^(1/x_s+omega_u) using
		// the GCD approach.
		final BigIntEuclidean gcd = BigIntEuclidean.calculate(sellerData.x_s.add(omega_u).mod(sharedMemory.p),
				sharedMemory.p);
		final Element T_U = (sharedMemory.g_n[0].add(sellerData.Y).add(sharedMemory.g_n[1].mul(d_dash))
				.add(sharedMemory.g_n[2].mul(s_u)).add(sharedMemory.g_n[3].mul(psi_uNum)))
						.mul(gcd.x.mod(sharedMemory.p)).getImmutable();

		/// Send T_U, d_dash, s_u, omega_u, psi_uNum, Y_I, Service, Price, Valid_Period.
		final ListData sendData = new ListData(Arrays.asList(T_U.toBytes(), d_dash.toByteArray(), s_u.toByteArray(),
				omega_u.toByteArray(), psi_uNum.toByteArray(), sellerData.Y_S.toBytes(), SellerData.TICKET_SERVICE,
				SellerData.TICKET_PRICE, sharedMemory.stringToBytes(sellerData.VP_T)));
		return sendData.toBytes();
	}

	/**
	 * Generates the ticket transcript data.
	 *
	 * @param data
	 *            The data received from the validator.
	 * @return The ticket transcript response data.
	 */
	private byte[] generateTicketTranscript(byte[] data) {
		// Note that all elliptic curve calculations are in an additive group
		// such that * -> + and ^ -> *.
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();
		final UserData userData = (UserData) sharedMemory.getData(Actor.USER);
		final Crypto crypto = Crypto.getInstance();

		// Decode the received data.
		final ListData listData = ListData.fromBytes(data);

		if (listData.getList().size() != 2) {
			LOG.error("wrong number of data elements: " + listData.getList().size());
			return null;
		}

		final byte[] ID_V = listData.getList().get(0);
		// TODO: check that ID_V has not asked us for a ticket before - ignored for
		// now...

		final BigInteger r = new BigInteger(listData.getList().get(1));

		// Select random pi, lambda, x_bar_u, s_bar_u, pi_bar, lambda_bar,
		// pi_bar_dash, lambda_bar_dash, omega_bar_u, d_bar_u
		final BigInteger pi = crypto.secureRandom(sharedMemory.p);
		final BigInteger lambda = crypto.secureRandom(sharedMemory.p);
		final BigInteger x_bar_u = crypto.secureRandom(sharedMemory.p);
		final BigInteger s_bar_u = crypto.secureRandom(sharedMemory.p);
		final BigInteger pi_bar = crypto.secureRandom(sharedMemory.p);
		final BigInteger pi_bar_dash = crypto.secureRandom(sharedMemory.p);
		final BigInteger lambda_bar = crypto.secureRandom(sharedMemory.p);
		final BigInteger omega_bar_u = crypto.secureRandom(sharedMemory.p);
		final BigInteger d_bar_u = crypto.secureRandom(sharedMemory.p);

		// Select random M_3_U
		final Element M_3_U = sharedMemory.pairing.getG1().newRandomElement().getImmutable();

		// Compute:
		// D = g^s_u
		// D_bar = g^s_bar_u
		final Element D = sharedMemory.g.mul(userData.s_u).getImmutable();
		final Element D_bar = sharedMemory.g.mul(s_bar_u).getImmutable();

		// Compute:
		// Ps_U = Y_U * g_1^d_u
		// Ps_bar_U = xi^x-bar_u*g_1^d_bar_u
		final Element Ps_U = userData.Y_U.add(sharedMemory.g_n[1].mul(userData.d_u)).getImmutable();
		final Element Ps_bar_U = (sharedMemory.xi.mul(x_bar_u)).add(sharedMemory.g_n[1].mul(d_bar_u)).getImmutable();

		final byte[] hashID_V = crypto.getHash(ID_V);
		final Element elementFromHashID_V = sharedMemory.pairing.getG1()
				.newElementFromHash(hashID_V, 0, hashID_V.length).getImmutable();
		// Compute:
		// E = Y_U * H'(ID_V)^(r*s_u)
		// E_bar = xi^x_bar_u * g_2^(r*s_bar_u)
		// F = T_U * theta^pi
		final Element E = (userData.Y_U).add(elementFromHashID_V.mul(r.multiply(userData.s_u).mod(sharedMemory.p)))
				.getImmutable();
		final Element E_bar = sharedMemory.xi.mul(x_bar_u)
				.add(elementFromHashID_V.mul(r.multiply(s_bar_u).mod(sharedMemory.p))).getImmutable();
		final Element F = userData.T_U.add(sharedMemory.theta.mul(pi)).getImmutable();

		// Compute:
		// J = g^pi * theta^lambda
		// J_bar = g^pi_bar * theta^lambda_bar
		// J_dash = J^omega_u
		// J_bar_dash = J^omega_bar_u
		final Element J = (sharedMemory.g.mul(pi).add(sharedMemory.theta.mul(lambda))).getImmutable();
		final Element J_bar = ((sharedMemory.g.mul(pi_bar)).add(sharedMemory.theta.mul(lambda_bar))).getImmutable();
		final Element J_dash = J.mul(userData.omega_u).getImmutable();
		final Element J_bar_dash = J.mul(omega_bar_u).getImmutable();

		// Compute:
		// R = e(F,Y_I) / (e(g_0,rho) e(Y,rho) e(g_3, rho)^psi_u
		// R_bar = e(xi,rho)^x_bar_u * e(g_1,rho)^d_bar_u * e(g_2,rho)^s_bar_u *
		// e(F,rho)^-omega_bar_u * e(theta,rho)^pi_bar_dash *
		// e(theta,rho)^pi_bar
		final Element R_1 = sharedMemory.pairing.pairing(F, userData.Y_S);
		final Element R_2 = sharedMemory.pairing.pairing(sharedMemory.g_n[0], sharedMemory.rho).getImmutable();
		final Element R_3 = sharedMemory.pairing.pairing(Ps_U, sharedMemory.rho).getImmutable();

		final Element R_4 = sharedMemory.pairing.pairing(sharedMemory.g_n[3], sharedMemory.rho).pow(userData.psi_uNum)
				.getImmutable();

		final Element R = R_1.div(R_2.mul(R_3).mul(R_4)).getImmutable();

		final Element R_bar1 = sharedMemory.pairing.pairing(sharedMemory.g_n[2], sharedMemory.rho).pow(s_bar_u)
				.getImmutable();
		final Element R_bar2 = sharedMemory.pairing.pairing(F, sharedMemory.rho)
				.pow(omega_bar_u.negate().mod(sharedMemory.p)).getImmutable();
		final Element R_bar3 = sharedMemory.pairing.pairing(sharedMemory.theta, sharedMemory.rho).pow(pi_bar_dash)
				.getImmutable();

		final Element R_bar4 = sharedMemory.pairing.pairing(sharedMemory.theta, userData.Y_S).pow(pi_bar)
				.getImmutable();
		final Element R_bar = R_bar1.mul(R_bar2).mul(R_bar3).mul(R_bar4).getImmutable();

		// Compute c = H(M_3_U || D || Ps_U|| E || J || J_dash || R || D_bar || PS_bar_U
		// ||E_bar
		// || J_bar || J_bar_dash || R_dash)
		final ListData cData = new ListData(Arrays.asList(M_3_U.toBytes(), D.toBytes(), Ps_U.toBytes(), E.toBytes(),
				J.toBytes(), J_dash.toBytes(), R.toBytes(), D_bar.toBytes(), Ps_bar_U.toBytes(), E_bar.toBytes(),
				J_bar.toBytes(), J_bar_dash.toBytes(), R_bar.toBytes()));
		final byte[] c = crypto.getHash(cData.toBytes());
		final BigInteger cNum = new BigInteger(1, c).mod(sharedMemory.p);

		// Compute:
		// s_BAR_u = s_bar_u - c*s_u
		// x_BAR_u = x_bar_u - c*x_u
		// s_hat_u = r*s_bar_u - c*r*s_u

		// d_BAR_u = d_bar_u – c*d_u
		// pi_BAR = pi_bar - c*pi
		// pi_BAR_dash = pi_bar_dash - c*pi
		// lambda_BAR = lambda_bar - c*lambda
		// omega_BAR_u = omega_bar_u - c*omega_u
		final BigInteger s_BAR_u = s_bar_u.subtract(cNum.multiply(userData.s_u)).mod(sharedMemory.p);
		final BigInteger x_BAR_u = x_bar_u.subtract(cNum.multiply(userData.x_u)).mod(sharedMemory.p);
		final BigInteger s_hat_u = r.multiply(s_bar_u).subtract(cNum.multiply(r).multiply(userData.s_u))
				.mod(sharedMemory.p);
		final BigInteger pi_BAR = pi_bar.subtract(cNum.multiply(pi)).mod(sharedMemory.p);
		final BigInteger lambda_BAR = lambda_bar.subtract(cNum.multiply(lambda)).mod(sharedMemory.p);
		final BigInteger omega_BAR_u = omega_bar_u.subtract(cNum.multiply(userData.omega_u)).mod(sharedMemory.p);
		final BigInteger pi_BAR_dash = pi_bar_dash.subtract(cNum.multiply(pi).multiply(userData.omega_u))
				.mod(sharedMemory.p);
		final BigInteger d_BAR_u = d_bar_u.subtract(cNum.multiply(userData.d_u)).mod(sharedMemory.p);

		// Sends P_U, Price, Service, VP_T, M_3_U, D, Ps_U, E, F, J, J_dash, R, c,
		// s_BAR_u, x_BAR_u, s_hat_u, pi_BAR, lambda_BAR, omega_BAR_u, pi_BAR_dash,
		// d_BAR_u, psi_uNum
		// U also needs to send Y_I as the verifier won't have it otherwise

		final ListData sendData = new ListData(Arrays.asList(sharedMemory.stringToBytes(userData.P_U), userData.price,
				userData.service, sharedMemory.stringToBytes(userData.VP_T), M_3_U.toBytes(), D.toBytes(),
				Ps_U.toBytes(), E.toBytes(), F.toBytes(), J.toBytes(), J_dash.toBytes(), R.toBytes(), c,
				s_BAR_u.toByteArray(), x_BAR_u.toByteArray(), s_hat_u.toByteArray(), pi_BAR.toByteArray(),
				lambda_BAR.toByteArray(), omega_BAR_u.toByteArray(), pi_BAR_dash.toByteArray(), d_BAR_u.toByteArray(),
				userData.psi_uNum.toByteArray(), userData.Y_S.toBytes()));

		return sendData.toBytes();
	}

	/**
	 * Generates the user's credentials.
	 *
	 * @param data
	 *            The data received from the user.
	 * @return The user's credential data.
	 */
	private byte[] generateUserCredentials(byte[] data) {
		// Note that all elliptic curve calculations are in an additive
		// group such that * -> + and ^ -> *.
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();
		final CentralAuthorityData centralAuthorityData = (CentralAuthorityData) sharedMemory
				.getData(Actor.CENTRAL_AUTHORITY);
		final Crypto crypto = Crypto.getInstance();

		// Decode the received data.
		final ListData listData = ListData.fromBytes(data);

		if (listData.getList().size() == 0) {// can vary dependent on user range and set policies
			LOG.error("wrong number of data elements: " + listData.getList().size());
			return null;
		}

		LOG.debug("number of data elements: " + listData.getList().size());
		int index = 0;
		final byte[] ID_U = listData.getList().get(index++);
		final Element M_1_U = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element Y_U = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element R = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final byte[] c_1 = listData.getList().get(index++);
		final BigInteger c_1Num = new BigInteger(1, c_1).mod(sharedMemory.p);
		final byte[] c_2 = listData.getList().get(index++);
		final BigInteger c_2Num = new BigInteger(1, c_2).mod(sharedMemory.p);
		final BigInteger s_1 = new BigInteger(listData.getList().get(index++));
		final BigInteger s_2 = new BigInteger(listData.getList().get(index++));

		final int numOfUserRanges = (new BigInteger(listData.getList().get(index++))).intValue();
		LOG.debug("Number of range policies: " + numOfUserRanges);
		final BigInteger[] A_U_range = new BigInteger[numOfUserRanges];
		for (int i = 0; i < numOfUserRanges; i++) {
			A_U_range[i] = new BigInteger(listData.getList().get(index++));
		}
		final int numOfUserSets = (new BigInteger(listData.getList().get(index++))).intValue();
		final String[] A_U_set = new String[numOfUserSets];
		LOG.debug("Number of set policies: " + numOfUserSets);

		for (int i = 0; i < numOfUserSets; i++) {
			A_U_set[i] = new String(listData.getList().get(index++));
		}

		final String VP_U = sharedMemory.stringFromBytes(listData.getList().get(index++));
		// NB the validity period could be changed by the CA if required.

		// Verify PI_1_U via c_1 and c_2.
		LOG.debug("Verifying PI_1_U c1:...");
		final Element check1 = sharedMemory.xi.mul(s_1).add(Y_U.mul(c_1Num));
		final ListData c_1VerifyData = new ListData(Arrays.asList(M_1_U.toBytes(), Y_U.toBytes(), check1.toBytes()));
		final byte[] c_1Verify = crypto.getHash(c_1VerifyData.toBytes());

		if (!Arrays.equals(c_1, c_1Verify)) {
			LOG.error("failed to verify PI_1_U: c_1");
			return null;

		}
		LOG.debug("SUCCESS: Verified PI_1_U c1:...");

		LOG.debug("Verifying PI_1_U c2:...");
		final Element check2 = sharedMemory.g_frak.mul(s_2).add(R.mul(c_2Num));
		final ListData c_2VerifyData = new ListData(Arrays.asList(M_1_U.toBytes(), R.toBytes(), check2.toBytes()));
		final byte[] c_2Verify = crypto.getHash(c_2VerifyData.toBytes());

		if (!Arrays.equals(c_2, c_2Verify)) {
			LOG.error("failed to verify PI_1_U: c_2");
			return null;

		}

		LOG.debug("SUCCESS: Verified PI_1_U c2:...");

		// Select random c_u and r_dash.
		final BigInteger c_u = crypto.secureRandom(sharedMemory.p);
		final BigInteger r_dash = crypto.secureRandom(sharedMemory.p);

		final byte[] vpuHash = crypto.getHash(VP_U.getBytes());
		final BigInteger vpuHashNum = new BigInteger(1, vpuHash).mod(sharedMemory.p);

		// Compute delta_U using the same GCD approach from above.
		final BigIntEuclidean gcd = BigIntEuclidean.calculate(centralAuthorityData.x.add(c_u).mod(sharedMemory.p),
				sharedMemory.p);

		Element sum1 = sharedMemory.pairing.getG1().newZeroElement();
		for (int i = 0; i < numOfUserRanges; i++) {
			final Element value = sharedMemory.g_hat_n[i].mul(A_U_range[i]).getImmutable();
			sum1 = sum1.add(value);
		}
		sum1 = sum1.getImmutable();

		Element sum2 = sharedMemory.pairing.getG1().newZeroElement();
		;
		for (int i = 0; i < numOfUserSets; i++) {
			final byte[] hash = crypto.getHash(A_U_set[i].getBytes());
			final BigInteger hashNum = new BigInteger(1, hash).mod(sharedMemory.p);
			final Element value = sharedMemory.eta_n[i].mul(hashNum).getImmutable();
			sum2 = sum2.add(value);
		}
		sum2 = sum2.getImmutable();

		Element delta_U = sharedMemory.g_n[0].add(sharedMemory.g_n[1].mul(vpuHashNum)).add(Y_U).add(R)
				.add(sharedMemory.g_frak.mul(r_dash).add(sum1).add(sum2)).getImmutable();
		delta_U = delta_U.mul(gcd.x.mod(sharedMemory.p)).getImmutable();

		// Store ID_U, A_U, Y_U and delta_U.
		centralAuthorityData.ID_U = ID_U;
		centralAuthorityData.A_U_range = A_U_range;
		centralAuthorityData.A_U_set = A_U_set;
		centralAuthorityData.Y_U = Y_U;
		centralAuthorityData.delta_U = delta_U;
		centralAuthorityData.VP_U = VP_U;

		// Send c_u, r_dash, delta_U, VP_U

		final ListData sendData = new ListData(Arrays.asList(c_u.toByteArray(), r_dash.toByteArray(), delta_U.toBytes(),
				sharedMemory.stringToBytes(VP_U)));
		return sendData.toBytes();
	}

	/**
	 * Generates the user identity data.
	 *
	 * @return The user identity response data.
	 */
	private byte[] generateUserIdentity() {
		// Note that all elliptic curve calculations are in an additive group
		// such that * -> + and ^ -> *.
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();
		final UserData userData = (UserData) sharedMemory.getData(Actor.USER);
		final Crypto crypto = Crypto.getInstance();

		// Select random x_u and compute Y_U = xi^x_u
		userData.x_u = crypto.secureRandom(sharedMemory.p);
		userData.Y_U = sharedMemory.xi.mul(userData.x_u);

		// Select random r and compute R = g_frak^r
		userData.r = crypto.secureRandom(sharedMemory.p);
		LOG.debug("r: " + userData.r);
		final Element R = sharedMemory.g_frak.mul(userData.r).getImmutable();

		// Compute proof PI_1_U = (M_1_U, Y_U, R, Y_dash_U, R_dash, c_1, c_2,
		// s_1, s_2):
		final BigInteger x_bar = crypto.secureRandom(sharedMemory.p);
		final BigInteger r_bar = crypto.secureRandom(sharedMemory.p);
		final Element M_1_U = sharedMemory.pairing.getG1().newRandomElement().getImmutable();

		final Element Y_dash_U = sharedMemory.xi.mul(x_bar).getImmutable();
		final Element R_dash = sharedMemory.g_frak.mul(r_bar).getImmutable();

		final ListData c_1Data = new ListData(
				Arrays.asList(M_1_U.toBytes(), userData.Y_U.toBytes(), Y_dash_U.toBytes()));
		final byte[] c_1 = crypto.getHash(c_1Data.toBytes());
		final BigInteger c_1Num = new BigInteger(1, c_1);

		final ListData c_2Data = new ListData(Arrays.asList(M_1_U.toBytes(), R.toBytes(), R_dash.toBytes()));
		final byte[] c_2 = crypto.getHash(c_2Data.toBytes());
		final BigInteger c_2Num = new BigInteger(1, c_2);

		final BigInteger s_1 = (x_bar.subtract(c_1Num.multiply(userData.x_u))).mod(sharedMemory.p);
		final BigInteger s_2 = r_bar.subtract(c_2Num.multiply(userData.r)).mod(sharedMemory.p);

		// Send ID_U, PI_1_U (which includes Y_U, R), A_U, VP_U
		final List<byte[]> list = new ArrayList<>();
		list.addAll(Arrays.asList(userData.ID_U, M_1_U.toBytes(), userData.Y_U.toBytes(), R.toBytes(), c_1, c_2,
				s_1.toByteArray(), s_2.toByteArray()));
		final BigInteger numOfUserRanges = BigInteger.valueOf(UserData.A_U_range.length);

		list.add(numOfUserRanges.toByteArray());
		for (final BigInteger attribute : UserData.A_U_range) {
			list.add(attribute.toByteArray());
		}

		final BigInteger numOfUserSets = BigInteger.valueOf(UserData.A_U_set.length);
		list.add(numOfUserSets.toByteArray());
		for (final String attribute : UserData.A_U_set) {
			list.add(attribute.getBytes(Data.UTF8));
		}
		list.add(sharedMemory.stringToBytes(userData.VP_U));

		final ListData sendData = new ListData(list);
		return sendData.toBytes();
	}

	private byte[] generateUserProof() { // was generateUserPseudonym
		final UserData userData = (UserData) sharedMemory.getData(Actor.USER);
		final Crypto crypto = Crypto.getInstance();

		// Select random c_bar, d, d_bar, alpha, alpha_bar, alpha_bar_dash,
		// beta, x_bar_u,
		// r_bar_u, c_bar_u
		final BigInteger c_bar = crypto.secureRandom(sharedMemory.p);
		final BigInteger d = crypto.secureRandom(sharedMemory.p);
		final BigInteger d_bar = crypto.secureRandom(sharedMemory.p);
		final BigInteger alpha = crypto.secureRandom(sharedMemory.p);
		final BigInteger alpha_bar = crypto.secureRandom(sharedMemory.p);
		final BigInteger alpha_bar_dash = alpha.multiply(c_bar);
		final BigInteger beta = crypto.secureRandom(sharedMemory.p);
		final BigInteger beta_bar = crypto.secureRandom(sharedMemory.p);
		final BigInteger beta_bar_dash = beta.multiply(c_bar);
		final BigInteger x_bar_u = crypto.secureRandom(sharedMemory.p);
		final BigInteger r_bar_u = crypto.secureRandom(sharedMemory.p);
		final BigInteger c_bar_u = crypto.secureRandom(sharedMemory.p);

		final int numOfUserRanges = UserData.A_U_range.length;
		final int numOfUserSets = UserData.A_U_set.length;

		// Select random gamma_1-N1, gamma_bar_1-N1, a_bar_1-N1, and
		// t_1-N1_0-(k-1), t_dash_1-N1_0-(k-1), t_bar_1-N1_0-(k-1),
		// t_bar_dash_1-N1_0-(k-1), w_bar_1-N1_0-(k-1), w_bar_dash_1-N1_0-(k-1)
		final BigInteger[] gamma_n = new BigInteger[numOfUserRanges];
		final BigInteger[] gamma_bar_n = new BigInteger[numOfUserRanges];
		final BigInteger[] a_bar_n = new BigInteger[numOfUserRanges];
		final BigInteger[][] t_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] t_dash_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];

		final BigInteger[][] t_bar_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] t_bar_dash_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] w_bar_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] w_bar_dash_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];

		// part 1 of range proof
		long rangeProofTiming = Instant.now().toEpochMilli();
		for (int i = 0; i < numOfUserRanges; i++) {
			gamma_n[i] = crypto.secureRandom(sharedMemory.p);
			gamma_bar_n[i] = crypto.secureRandom(sharedMemory.p);
			a_bar_n[i] = crypto.secureRandom(sharedMemory.p);

			for (int j = 0; j < sharedMemory.k; j++) {
				t_n_m[i][j] = crypto.secureRandom(sharedMemory.p);
				t_dash_n_m[i][j] = crypto.secureRandom(sharedMemory.p);
				t_bar_n_m[i][j] = crypto.secureRandom(sharedMemory.p);
				t_bar_dash_n_m[i][j] = crypto.secureRandom(sharedMemory.p);
				w_bar_n_m[i][j] = crypto.secureRandom(sharedMemory.p);
				w_bar_dash_n_m[i][j] = crypto.secureRandom(sharedMemory.p);
			}
		}
		// end of part 1
		rangeProofTiming = Instant.now().toEpochMilli() - rangeProofTiming;

		// part 1 of set proof
		long setProofTiming = Instant.now().toEpochMilli();
		// Select random e_1-N2, e_bar_1-N2, e_hat_1-N2
		final BigInteger[] e_n = new BigInteger[numOfUserSets];
		final BigInteger[] e_bar_n = new BigInteger[numOfUserSets];
		final BigInteger[] e_hat_n = new BigInteger[numOfUserSets];
		for (int i = 0; i < numOfUserSets; i++) {
			e_n[i] = crypto.secureRandom(sharedMemory.p);
			e_bar_n[i] = crypto.secureRandom(sharedMemory.p);
			e_hat_n[i] = crypto.secureRandom(sharedMemory.p);
		}
		// end of part 1
		setProofTiming = Instant.now().toEpochMilli() - setProofTiming;

		// Select random M_2_U
		final Element M_2_U = sharedMemory.pairing.getG1().newRandomElement().getImmutable();

		// Compute C = delta_U * theta^alpha
		final Element C = userData.delta_U.add(sharedMemory.theta.mul(alpha)).getImmutable();

		// Compute D = g^alpha * theta^beta
		final Element D = sharedMemory.g.mul(alpha).add(sharedMemory.theta.mul(beta)).getImmutable();

		// Compute phi = D^c_u=g^alpha_dash * theta^beta_dash where alpha_dash =
		// alpha*c_u and
		// beta_dash = beta*c_u
		final BigInteger alpha_dash = alpha.multiply(userData.c_u);
		final BigInteger beta_dash = beta.multiply(userData.c_u);
		// final Element phi =
		// sharedMemory.g.mul(alpha_dash).add(sharedMemory.theta.mul(beta_dash))

		final Element phi = D.mul(userData.c_u).getImmutable();
		// Compute Y = xi^x_u * g_1^d
		final Element Y = sharedMemory.xi.mul(userData.x_u).add(sharedMemory.g_n[1].mul(d)).getImmutable();

		// Compute:
		// Z_1-N1 = g^gamma_1-N1 * h^a_1-N1,
		// Z_dash_1-N1 = g^gamma_bar_1-N1 * h^a_bar_1-N1
		// Z_bar_1-N1 = g^gamma_bar_1-N1 *
		// PRODUCT_0-(k-1)(h_bar_i^w_bar_1-N1_0-(k-1))
		// Z_bar_dash_1-N1 = g^gamma_bar_1-N1 *
		// PRODUCT_0-(k-1)(h_bar_i^w_bar_dash_1-N1_0-(k-1))

		final Element[] Z_n = new Element[numOfUserRanges];
		final Element[] Z_dash_n = new Element[numOfUserRanges];
		final Element[] Z_bar_n = new Element[numOfUserRanges];
		final Element[] Z_bar_dash_n = new Element[numOfUserRanges];

		// part 2 of range proof
		rangeProofTiming = rangeProofTiming - Instant.now().toEpochMilli();
		for (int i = 0; i < numOfUserRanges; i++) {
			Z_n[i] = sharedMemory.g.mul(gamma_n[i]).add(sharedMemory.h.mul(UserData.A_U_range[i])).getImmutable();
			Z_dash_n[i] = sharedMemory.g.mul(gamma_bar_n[i]).add(sharedMemory.h.mul(a_bar_n[i]).getImmutable());
			// LOG.debug("Z_dash_n["+i+"]= "+ Z_dash_n[i]);

			Element sum1 = sharedMemory.g.mul(gamma_bar_n[i]).getImmutable();
			for (int j = 0; j < sharedMemory.k; j++) {
				final Element value = sharedMemory.h_bar_n[j].mul(w_bar_n_m[i][j]).getImmutable();
				sum1 = sum1.add(value).getImmutable();
			}
			Z_bar_n[i] = sum1.getImmutable();

			Element sum2 = sharedMemory.g.mul(gamma_bar_n[i]).getImmutable();
			for (int j = 0; j < sharedMemory.k; j++) {
				final Element value = sharedMemory.h_bar_n[j].mul(w_bar_dash_n_m[i][j]).getImmutable();
				sum2 = sum2.add(value).getImmutable();
			}
			Z_bar_dash_n[i] = sum2.getImmutable();
		}

		// Compute w_n_m and w_dash_n_m
		final int[][] w_n_m = new int[numOfUserRanges][sharedMemory.k];
		final int[][] w_dash_n_m = new int[numOfUserRanges][sharedMemory.k];

		for (int i = 0; i < numOfUserRanges; i++) {
			// Calculate w_l_i member of [0, q-1], and since q = 2, w_l_i is
			// binary. Here w_l_i represents which bits are set in the
			// number A_U_range[i] - lower bound of range policy[i]
			final BigInteger lowerDiff = UserData.A_U_range[i]
					.subtract(BigInteger.valueOf(sharedMemory.rangePolicies[i][0]));
			final String reverseLowerDiff = new StringBuilder(lowerDiff.toString(sharedMemory.q)).reverse().toString();

			// Calculate w_dash_l_i member of [0, q-1], and since q = 2,
			// w_dash_l_i is binary. Here w_dash_l_i represents which bits
			// are set in the number A_U_range[i] - upper bound of range
			// policy[i] + q^k
			final BigInteger upperDiff = UserData.A_U_range[i]
					.subtract(BigInteger.valueOf(sharedMemory.rangePolicies[i][1]))
					.add(BigInteger.valueOf(sharedMemory.q).pow(sharedMemory.k));
			final String reverseUpperDiff = new StringBuilder(upperDiff.toString(sharedMemory.q)).reverse().toString();

			for (int j = 0; j < sharedMemory.k; j++) {
				if (j < reverseLowerDiff.length()) {
					w_n_m[i][j] = Integer.parseInt(Character.toString(reverseLowerDiff.charAt(j)));
				} else {
					w_n_m[i][j] = 0;
				}
				if (j < reverseUpperDiff.length()) {
					w_dash_n_m[i][j] = Integer.parseInt(Character.toString(reverseUpperDiff.charAt(j)));
				} else {
					w_dash_n_m[i][j] = 0;
				}
			}
		}

		// Compute:
		// A_w_1-N1_0-(k-1) = h_w_1-N1_0-(k-1)^t_1-N1_0-(k-1)
		// A_dash_w_1-N1_0-(k-1) = h_w_dash_1-N1_0-(k-1)^t_dash_1-N1_0-(k-1)
		// V_1-N1_0-(k-1) = e(h, h)^t_1-N1_0-(k-1) * e(A_w_1-N1_0-(k-1),
		// h)^-w_1-N1_0-(k-1)
		// V_bar_1-N1_0-(k-1) = e(h, h)^t_bar_1-N1_0-(k-1) * e(A_w_1-N1_0-(k-1),
		// h)^-w_bar_1-N1_0-(k-1)
		// V_dash_1-N1_0-(k-1) = e(h, h)^t_dash_1-N1_0-(k-1) *
		// e(A_dash_w_1-N1_0-(k-1), h)^-w_dash_1-N1_0-(k-1)
		// V_bar_dash_1-N1_0-(k-1) = e(h, h)^t_bar_dash_1-N1_0-(k-1) *
		// e(A_dash_w_1-N1_0-(k-1), h)^-w_bar_dash_1-N1_0-(k-1)
		final Element[][] A_n_m = new Element[numOfUserRanges][sharedMemory.k];
		final Element[][] A_dash_n_m = new Element[numOfUserRanges][sharedMemory.k];
		final Element[][] V_n_m = new Element[numOfUserRanges][sharedMemory.k];
		final Element[][] V_bar_n_m = new Element[numOfUserRanges][sharedMemory.k];
		final Element[][] V_dash_n_m = new Element[numOfUserRanges][sharedMemory.k];
		final Element[][] V_bar_dash_n_m = new Element[numOfUserRanges][sharedMemory.k];

		for (int i = 0; i < numOfUserRanges; i++) {
			for (int j = 0; j < sharedMemory.k; j++) {
				A_n_m[i][j] = sharedMemory.h_n[w_n_m[i][j]].mul(t_n_m[i][j]).getImmutable();
				A_dash_n_m[i][j] = sharedMemory.h_n[w_dash_n_m[i][j]].mul(t_dash_n_m[i][j]).getImmutable();

				V_n_m[i][j] = sharedMemory.pairing.pairing(sharedMemory.h, sharedMemory.h).pow(t_n_m[i][j])
						.mul(sharedMemory.pairing.pairing(A_n_m[i][j], sharedMemory.h)
								.pow(BigInteger.valueOf(w_n_m[i][j]).negate().mod(sharedMemory.p)))
						.getImmutable();
				V_bar_n_m[i][j] = sharedMemory.pairing
						.pairing(sharedMemory.h, sharedMemory.h).pow(t_bar_n_m[i][j]).mul(sharedMemory.pairing
								.pairing(A_n_m[i][j], sharedMemory.h).pow(w_bar_n_m[i][j].negate().mod(sharedMemory.p)))
						.getImmutable();

				V_dash_n_m[i][j] = (sharedMemory.pairing.pairing(sharedMemory.h, sharedMemory.h).pow(t_dash_n_m[i][j]))
						.mul(sharedMemory.pairing.pairing(A_dash_n_m[i][j], sharedMemory.h)
								.pow(BigInteger.valueOf(w_dash_n_m[i][j]).negate().mod(sharedMemory.p)))
						.getImmutable();
				V_bar_dash_n_m[i][j] = sharedMemory.pairing.pairing(sharedMemory.h, sharedMemory.h)
						.pow(t_bar_dash_n_m[i][j]).mul(sharedMemory.pairing.pairing(A_dash_n_m[i][j], sharedMemory.h)
								.pow(w_bar_dash_n_m[i][j].negate().mod(sharedMemory.p)))
						.getImmutable();
			}
		}
		// end of part 2 of range proof
		rangeProofTiming = Instant.now().toEpochMilli() + rangeProofTiming;

		// Compute D_bar = g^alpha_bar * theta^beta_bar
		final Element D_bar = sharedMemory.g.mul(alpha_bar).add(sharedMemory.theta.mul(beta_bar)).getImmutable();
		// LOG.debug("D_bar="+D_bar);

		// Compute phi_bar = D^c_bar
		final Element phi_bar = D.mul(c_bar).getImmutable();
		// LOG.debug("phi_bar="+phi_bar);

		// Compute Y_bar = xi^x_bar_u * g_1^d_bar
		final Element Y_bar = sharedMemory.xi.mul(x_bar_u).add(sharedMemory.g_n[1].mul(d_bar)).getImmutable();
		// LOG.debug("Y_bar="+Y_bar);

		// Compute:
		// R = e(C,g_bar) / (e(g_0,g) e(g_1,g)^H(VP_U)
		// R_dash = e(xi,g)^x_bar_u * e(g_frak,g)^r_bar_u *
		// PRODUCT_1-N1(e(g_hat,g)^a_bar_l * PRODUCT_1-N2(e(eta_i,g)^e_hat_i * e
		// (C,g)^c_bar_u * e(theta,g)^a_bar_dash * e(theta,g_bar)^alpha_bar

		final byte[] vpuHash = crypto.getHash(userData.VP_U.getBytes());
		final BigInteger vpuHashNum = new BigInteger(1, vpuHash).mod(sharedMemory.p);

		final Element R_1 = sharedMemory.pairing.pairing(C, sharedMemory.g_bar).getImmutable();
		final Element R_2 = sharedMemory.pairing.pairing(sharedMemory.g_n[0], sharedMemory.g).getImmutable();
		final Element R_3 = sharedMemory.pairing.pairing(sharedMemory.g_n[1], sharedMemory.g).pow(vpuHashNum)
				.getImmutable();
		final Element R = R_1.div(R_2.mul(R_3)).getImmutable();

		// part 3 of range proof
		rangeProofTiming = rangeProofTiming - Instant.now().toEpochMilli();
		final Element R_dash1 = sharedMemory.pairing.pairing(sharedMemory.xi, sharedMemory.g).pow(x_bar_u)
				.getImmutable();
		final Element R_dash2 = sharedMemory.pairing.pairing(sharedMemory.g_frak, sharedMemory.g).pow(r_bar_u)
				.getImmutable();

		Element product1 = sharedMemory.pairing.getGT().newOneElement().getImmutable();
		for (int i = 0; i < numOfUserRanges; i++) {
			final Element value = sharedMemory.pairing.pairing(sharedMemory.g_hat_n[i], sharedMemory.g).pow(a_bar_n[i]);
			product1 = product1.mul(value);
		}
		// end of part 3 of range proof
		rangeProofTiming = Instant.now().toEpochMilli() + rangeProofTiming;

		// part 3 of set proof
		setProofTiming = setProofTiming - Instant.now().toEpochMilli();
		Element product2 = sharedMemory.pairing.getGT().newOneElement().getImmutable();

		for (int i = 0; i < numOfUserSets; i++) {
			final Element value = sharedMemory.pairing.pairing(sharedMemory.eta_n[i], sharedMemory.g).pow(e_hat_n[i]);
			product2 = product2.mul(value);
		}
		// end of part 3 of set proof
		setProofTiming = setProofTiming + Instant.now().toEpochMilli();

		final Element R_dash3 = sharedMemory.pairing.pairing(C, sharedMemory.g)
				.pow(c_bar_u.negate().mod(sharedMemory.p));
		final Element R_dash4 = sharedMemory.pairing.pairing(sharedMemory.theta, sharedMemory.g).pow(alpha_bar_dash);
		final Element R_dash5 = sharedMemory.pairing.pairing(sharedMemory.theta, sharedMemory.g_bar).pow(alpha_bar);

		final Element R_dash = R_dash1.mul(R_dash2).mul(product1).mul(product2).mul(R_dash3).mul(R_dash4).mul(R_dash5)
				.getImmutable();
		// LOG.debug("R_dash = "+R_dash);

		// Compute:
		// B_1-N2_j = eta_1-N2_j^e_1-N2
		// W_1-N2_j = e(B_1-N2_j,eta_bar_1-N2)
		// W_bar_1-N2_j = e(eta,eta_1-N2)^e_bar_1-N2 *
		// e(B_1-N2_j,eta_1-N2)^e_hat_1-N2
		//
		// Note here we are only required to select one of the j set policies,
		// but for completeness, and to ensure that we measure
		// the maximum possible timing for the protocol, we have selected a
		// value for all possible set values zeta.

		// part 4 of set proof
		setProofTiming = setProofTiming - Instant.now().toEpochMilli();

		final Element[][] B_n_m = new Element[numOfUserSets][sharedMemory.biggestSetSize];
		final Element[][] W_n_m = new Element[numOfUserSets][sharedMemory.biggestSetSize];
		final Element[][] W_bar_n_m = new Element[numOfUserSets][sharedMemory.biggestSetSize];

		for (int i = 0; i < numOfUserSets; i++) {
			final int currentSetSize = sharedMemory.zeta(i);
			for (int j = 0; j < sharedMemory.biggestSetSize; j++) {
				if ((j < currentSetSize) && UserData.A_U_set[i].equalsIgnoreCase(sharedMemory.setPolices[i][j])) {
					B_n_m[i][j] = sharedMemory.eta_n_n[i][j].mul(e_n[i]).getImmutable();
					W_n_m[i][j] = sharedMemory.pairing.pairing(B_n_m[i][j], sharedMemory.eta_bar_n[i]).getImmutable();
					Element part1 = sharedMemory.pairing.pairing(sharedMemory.eta, sharedMemory.eta_n[i])
							.pow(e_bar_n[i]).getImmutable();
					Element part2 = sharedMemory.pairing.pairing(B_n_m[i][j], sharedMemory.eta_n[i]).pow(e_hat_n[i])
							.getImmutable();
					W_bar_n_m[i][j] = part1.mul(part2).getImmutable();
				} else {
					// just stick some fixed element here... as they won't be used...
					B_n_m[i][j] = sharedMemory.g;
					W_n_m[i][j] = sharedMemory.gt;
					W_bar_n_m[i][j] = sharedMemory.gt;

				}
			}
		}
		// end of part 4 of set proof
		setProofTiming = setProofTiming + Instant.now().toEpochMilli();

		// Calculate hash c_BAR
		final List<byte[]> c_BARList = new ArrayList<>();
		c_BARList.addAll(Arrays.asList(M_2_U.toBytes(), Y.toBytes(), Y_bar.toBytes(), D.toBytes(), D_bar.toBytes(),
				phi.toBytes(), phi_bar.toBytes(), C.toBytes(), R.toBytes(), R_dash.toBytes()));

		// LOG.debug("Original Hash so far: "+base64.encodeToString(crypto.getHash((new
		// ListData(c_BARList)).toBytes())));
		for (int i = 0; i < numOfUserRanges; i++) {
			c_BARList.add(Z_n[i].toBytes());
		}

		// LOG.debug("Z_n: Original Hash so far:
		// "+base64.encodeToString(crypto.getHash((new
		// ListData(c_BARList)).toBytes())));

		for (int i = 0; i < numOfUserRanges; i++) {
			c_BARList.add(Z_dash_n[i].toBytes());
		}

		// LOG.debug("Z_dash_n : Original Hash so far:
		// "+base64.encodeToString(crypto.getHash((new
		// ListData(c_BARList)).toBytes())));
		for (int i = 0; i < numOfUserSets; i++) {
			for (int j = 0; j < sharedMemory.biggestSetSize; j++) {
				c_BARList.add(B_n_m[i][j].toBytes());
			}
		}
		// LOG.debug("B_n_m: Original Hash so far:
		// "+base64.encodeToString(crypto.getHash((new
		// ListData(c_BARList)).toBytes())));

		for (int i = 0; i < numOfUserSets; i++) {
			for (int j = 0; j < sharedMemory.biggestSetSize; j++) {
				// LOG.debug("Original W_n_m["+i+"]["+j+"] = "+W_n_m[i][j]);
				c_BARList.add(W_n_m[i][j].toBytes());
			}
		}

		// LOG.debug("W_n_m: Original Hash so far:
		// "+base64.encodeToString(crypto.getHash((new
		// ListData(c_BARList)).toBytes())));

		for (int i = 0; i < numOfUserSets; i++) {
			for (int j = 0; j < sharedMemory.biggestSetSize; j++) {
				// LOG.debug("W_bar_n_m["+i+"]["+j+"] = "+W_bar_n_m[i][j]);
				c_BARList.add(W_bar_n_m[i][j].toBytes());
			}
		}
		// LOG.debug("W_bar_n_m: Final Original Hash is:
		// "+base64.encodeToString(crypto.getHash((new
		// ListData(c_BARList)).toBytes())));
		final ListData c_BARData = new ListData(c_BARList);
		final byte[] c_BAR = crypto.getHash(c_BARData.toBytes());
		// LOG.debug("c_BAR= "+base64.encodeToString(c_BAR));

		final BigInteger c_BARNum = new BigInteger(1, c_BAR).mod(sharedMemory.p);
		// LOG.debug("c_BARNum after mod p="+c_BARNum);

		// Compute:
		// x_BAR_u = x_bar_u - c_BAR * x_u
		// d_BAR = d_bar - c_BAR * d
		// r_BAR_u = r_bar_u - c_BAR * r_u
		final BigInteger x_BAR_u = x_bar_u.subtract(c_BARNum.multiply(userData.x_u)).mod(sharedMemory.p);

		final BigInteger d_BAR = d_bar.subtract(c_BARNum.multiply(d)).mod(sharedMemory.p);
		final BigInteger r_BAR_u = r_bar_u.subtract(c_BARNum.multiply(userData.r_u)).mod(sharedMemory.p);

		// Compute:
		// gammac_BAR_1-N1 = gamma_bar_1-N1 - c_BAR * gamma_1-N1
		// ac_BAR_1-N1 = a_bar_1-N1 - c_BAR * a_1-N1
		final BigInteger[] gammac_BAR_n = new BigInteger[numOfUserRanges];
		final BigInteger[] ac_BAR_n = new BigInteger[numOfUserRanges];

		// part 4 of the range proof
		rangeProofTiming = rangeProofTiming - Instant.now().toEpochMilli();
		for (int i = 0; i < numOfUserRanges; i++) {
			gammac_BAR_n[i] = gamma_bar_n[i].subtract(c_BARNum.multiply(gamma_n[i])).mod(sharedMemory.p);
			ac_BAR_n[i] = a_bar_n[i].subtract(c_BARNum.multiply(UserData.A_U_range[i])).mod(sharedMemory.p);
		}
		// end of part 4 of the range proof
		rangeProofTiming = rangeProofTiming + Instant.now().toEpochMilli();

		// part 5 of set proof
		setProofTiming = setProofTiming - Instant.now().toEpochMilli();

		// Compute:
		// e_BAR_1-N2 = e_bar_1-N2 - c_BAR * e_1-N2
		// e_BAR_dash_1-N2 = e_hat_1-N2 - c_BAR * H(I_1-N2_j)
		final BigInteger[] e_BAR_n = new BigInteger[numOfUserSets];
		final BigInteger[] e_BAR_dash_n = new BigInteger[numOfUserSets];
		final BigInteger[] e_BAR_dash_dash_n = new BigInteger[numOfUserSets];

		for (int i = 0; i < numOfUserSets; i++) {
			e_BAR_n[i] = e_bar_n[i].subtract(c_BARNum.multiply(e_n[i])).mod(sharedMemory.p);

			final byte[] hash = crypto.getHash(UserData.A_U_set[i].getBytes(Data.UTF8));
			final BigInteger hashNum = new BigInteger(1, hash).mod(sharedMemory.p);

			e_BAR_dash_n[i] = e_hat_n[i].subtract(c_BARNum.multiply(hashNum)).mod(sharedMemory.p); // needed for R'
																									// verification
			e_BAR_dash_dash_n[i] = e_hat_n[i].add(c_BARNum.multiply(hashNum)).mod(sharedMemory.p); // needed for
																									// W_bar_n_m
																									// verification
		}
		// end of part 5 of set proof
		setProofTiming = setProofTiming + Instant.now().toEpochMilli();

		// Compute:
		// c_BAR_u = c_bar_u - c_BAR * c_u
		// alpha_BAR = alpha_bar - c_BAR * alpha
		// beta_BAR = beta_bar - c_BAR * beta
		// alpha_BAR_dash = alpha_bar_dash - c_BAR * alpha_dash
		// beta_BAR_dash = beta_bar_dash - c_BAR * beta_dash
		// LOG.debug("c_BARNum= "+c_BARNum);
		final BigInteger c_BAR_u = c_bar_u.subtract(c_BARNum.multiply(userData.c_u)).mod(sharedMemory.p);
		final BigInteger alpha_BAR = alpha_bar.subtract(c_BARNum.multiply(alpha)).mod(sharedMemory.p);
		final BigInteger beta_BAR = beta_bar.subtract(c_BARNum.multiply(beta)).mod(sharedMemory.p);
		final BigInteger alpha_BAR_dash = alpha_bar_dash.subtract(c_BARNum.multiply(alpha_dash)).mod(sharedMemory.p);
		// LOG.debug("alpha_BAR_dash= "+alpha_BAR_dash);
		final BigInteger beta_BAR_dash = beta_bar_dash.subtract(c_BARNum.multiply(beta_dash)).mod(sharedMemory.p);
		// LOG.debug("beta_BAR_dash= "+beta_BAR_dash);
		// LOG.debug("phi_bar= "+phi_bar);

		// part 5 of the range proof
		rangeProofTiming = rangeProofTiming - Instant.now().toEpochMilli();
		// Compute hashes e_BAR_1-N1
		final byte[][] e_BAR_m = new byte[numOfUserRanges][];
		final BigInteger[] e_BAR_mNum = new BigInteger[numOfUserRanges];

		for (int i = 0; i < numOfUserRanges; i++) {
			final ListData data = new ListData(Arrays.asList(M_2_U.toBytes(), Z_n[i].toBytes(), Z_dash_n[i].toBytes(),
					Z_bar_n[i].toBytes(), Z_bar_dash_n[i].toBytes()));
			e_BAR_m[i] = crypto.getHash(data.toBytes());
			e_BAR_mNum[i] = new BigInteger(1, e_BAR_m[i]).mod(sharedMemory.p);
		}

		// Compute:
		// gammae_BAR_1-N1 = gamma_bar_1-N1 - e_bar_1-N1 * gamma_1-N1
		// ae_BAR_1-N1 = a_bar_1-N1 - e_BAR_1-N1 * (a_1-N1 - c_1-N1)
		// ae_BAR_dash_1-N1 = a_bar_1-N1 - e_BAR_1-N1 * (a_1-N1 - d_k + q^k)
		final BigInteger[] gammae_BAR_n = new BigInteger[numOfUserRanges];
		final BigInteger[] ae_BAR_n = new BigInteger[numOfUserRanges];
		final BigInteger[] ae_BAR_dash_n = new BigInteger[numOfUserRanges];
		final BigInteger limit = BigInteger.valueOf((long) Math.pow(sharedMemory.q, sharedMemory.k));

		for (int i = 0; i < numOfUserRanges; i++) {
			gammae_BAR_n[i] = (gamma_bar_n[i].subtract(e_BAR_mNum[i].multiply(gamma_n[i]))).mod(sharedMemory.p);

			final BigInteger lower = BigInteger.valueOf(sharedMemory.rangePolicies[i][0]);
			ae_BAR_n[i] = (a_bar_n[i].subtract(e_BAR_mNum[i].multiply(UserData.A_U_range[i].subtract(lower))))
					.mod(sharedMemory.p);

			final BigInteger upper = BigInteger.valueOf(sharedMemory.rangePolicies[i][1]);
			ae_BAR_dash_n[i] = a_bar_n[i]
					.subtract(e_BAR_mNum[i].multiply(UserData.A_U_range[i].subtract(upper).add(limit)));

		}

		// Compute:
		// we_BAR_1-N1_0-(k-1) = w_bar_1-N1_0-(k-1) - e_BAR_1-N1 *
		// w_1-N1_0-(k-1)
		// we_BAR_dash_1-N1_0-(k-1) = w_bar_dash_1-N1_0-(k-1) - e_BAR_1-N1 *
		// w_dash_1-N1_0-(k-1)
		final BigInteger[][] we_BAR_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] we_BAR_dash_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];

		for (int i = 0; i < numOfUserRanges; i++) {
			for (int j = 0; j < sharedMemory.k; j++) {
				we_BAR_n_m[i][j] = w_bar_n_m[i][j].subtract(e_BAR_mNum[i].multiply(BigInteger.valueOf(w_n_m[i][j])))
						.mod(sharedMemory.p);
				we_BAR_dash_n_m[i][j] = w_bar_dash_n_m[i][j]
						.subtract(e_BAR_mNum[i].multiply(BigInteger.valueOf(w_dash_n_m[i][j]))).mod(sharedMemory.p);
			}
		}

		// Compute hash d_BAR_1-N1_0-(k-1)
		final byte[][][] d_BAR_n_m = new byte[numOfUserRanges][sharedMemory.k][];
		final BigInteger[][] d_BAR_n_mNum = new BigInteger[numOfUserRanges][sharedMemory.k];

		for (int i = 0; i < numOfUserRanges; i++) {
			for (int j = 0; j < sharedMemory.k; j++) {
				final ListData data = new ListData(Arrays.asList(M_2_U.toBytes(), A_n_m[i][j].toBytes(),
						A_dash_n_m[i][j].toBytes(), V_n_m[i][j].toBytes(), V_dash_n_m[i][j].toBytes(),
						V_bar_n_m[i][j].toBytes(), V_bar_dash_n_m[i][j].toBytes()));
				d_BAR_n_m[i][j] = crypto.getHash(data.toBytes());
				d_BAR_n_mNum[i][j] = new BigInteger(1, d_BAR_n_m[i][j]).mod(sharedMemory.p);
			}
		}

		// Compute:
		// t_BAR_1-N1_0-(k-1) = t_bar_1-N1_0-(k-1) - d_BAR_1-N1_0-(k-1) *
		// t_1-N1_0-(k-1)
		// t_BAR_dash_1-N1_0-(k-1) = t_bar_dash_1-N1_0-(k-1) -
		// d_BAR_1-N1_0-(k-1) * t_dash_1-N1_0-(k-1)
		// wd_BAR_1-N1_0-(k-1) = w_bar_1-N1_0-(k-1) - d_BAR_1-N1_0-(k-1) *
		// w_1-N1_0-(k-1)
		// wd_BAR_dash_1-N1_0-(k-1) = w_bar_dash_1-N1_0-(k-1) -
		// d_BAR_1-N1_0-(k-1) * w_dash_1-N1_0-(k-1)
		final BigInteger[][] t_BAR_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] t_BAR_dash_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] wd_BAR_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] wd_BAR_dash_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];

		for (int i = 0; i < numOfUserRanges; i++) {
			for (int j = 0; j < sharedMemory.k; j++) {
				t_BAR_n_m[i][j] = t_bar_n_m[i][j].subtract(d_BAR_n_mNum[i][j].multiply(t_n_m[i][j]))
						.mod(sharedMemory.p);
				t_BAR_dash_n_m[i][j] = t_bar_dash_n_m[i][j].subtract(d_BAR_n_mNum[i][j].multiply(t_dash_n_m[i][j]))
						.mod(sharedMemory.p);
				wd_BAR_n_m[i][j] = w_bar_n_m[i][j]
						.subtract(d_BAR_n_mNum[i][j].multiply(BigInteger.valueOf(w_n_m[i][j]))).mod(sharedMemory.p);
				wd_BAR_dash_n_m[i][j] = w_bar_dash_n_m[i][j]
						.subtract(d_BAR_n_mNum[i][j].multiply(BigInteger.valueOf(w_dash_n_m[i][j])))
						.mod(sharedMemory.p);
			}
		}

		// end of part 5 of the range proof
		rangeProofTiming = rangeProofTiming + Instant.now().toEpochMilli();
		addTimings("RangeSetProof-0010: Number of user ranges", numOfUserRanges);
		addTimings("RangeSetProof-0020: Range proof creation", rangeProofTiming);
		LOG.debug("***************************************************************************************");
		LOG.debug("Total timing for the range proof (ms): " + rangeProofTiming);
		LOG.debug("which involved N1 ranges where N1= " + numOfUserRanges);
		LOG.debug("***************************************************************************************");

		addTimings("RangeSetProof-0040: Number of user sets", numOfUserSets);
		addTimings("RangeSetProof-0050: Set proof creation", setProofTiming);
		LOG.debug("***************************************************************************************");
		LOG.debug("Total timing for the set proof (ms): " + setProofTiming);
		LOG.debug("which involved N2 sets where N2= " + numOfUserSets);
		LOG.debug("***************************************************************************************");

		// Save d, Y for later.
		userData.d = d;
		userData.Y = Y; // the user pseudonym

		// Send PI_2_U, which includes Y.
		final List<byte[]> sendDataList = new ArrayList<>();
		sendDataList.addAll(
				Arrays.asList(M_2_U.toBytes(), C.toBytes(), D.toBytes(), phi.toBytes(), Y.toBytes(), R.toBytes()));

		// transmit the number of range policies the user has
		sendDataList.add((BigInteger.valueOf(numOfUserRanges)).toByteArray());

		// transmit the number of set policies the user has
		sendDataList.add((BigInteger.valueOf(numOfUserSets)).toByteArray());

		for (int i = 0; i < numOfUserRanges; i++) {
			sendDataList.add(Z_n[i].toBytes());
			sendDataList.add(Z_dash_n[i].toBytes());
			sendDataList.add(Z_bar_n[i].toBytes());
			sendDataList.add(Z_bar_dash_n[i].toBytes());

			for (int j = 0; j < sharedMemory.k; j++) {
				sendDataList.add(A_n_m[i][j].toBytes());
				sendDataList.add(A_dash_n_m[i][j].toBytes());
				sendDataList.add(V_n_m[i][j].toBytes());
				sendDataList.add(V_bar_n_m[i][j].toBytes());
				sendDataList.add(V_dash_n_m[i][j].toBytes());
				sendDataList.add(V_bar_dash_n_m[i][j].toBytes());
			}
		}

		for (int i = 0; i < numOfUserSets; i++) {
			for (int j = 0; j < sharedMemory.biggestSetSize; j++) {
				sendDataList.add(B_n_m[i][j].toBytes());
				sendDataList.add(W_n_m[i][j].toBytes());
				sendDataList.add(W_bar_n_m[i][j].toBytes());
			}
		}

		sendDataList.add(c_BAR);
		sendDataList.add(c_BAR_u.toByteArray());
		sendDataList.add(x_BAR_u.toByteArray());
		sendDataList.add(d_BAR.toByteArray());
		sendDataList.add(r_BAR_u.toByteArray());
		sendDataList.add(alpha_BAR.toByteArray());
		sendDataList.add(beta_BAR.toByteArray());
		sendDataList.add(alpha_BAR_dash.toByteArray());
		sendDataList.add(beta_BAR_dash.toByteArray());

		for (int i = 0; i < numOfUserRanges; i++) {
			sendDataList.add(e_BAR_m[i]);

			sendDataList.add(gammac_BAR_n[i].toByteArray());
			sendDataList.add(ac_BAR_n[i].toByteArray());

			sendDataList.add(gammae_BAR_n[i].toByteArray());
			sendDataList.add(ae_BAR_n[i].toByteArray());
			sendDataList.add(ae_BAR_dash_n[i].toByteArray());
		}

		for (int i = 0; i < numOfUserSets; i++) {
			sendDataList.add(e_BAR_n[i].toByteArray());
			sendDataList.add(e_BAR_dash_n[i].toByteArray());
			sendDataList.add(e_BAR_dash_dash_n[i].toByteArray());
		}

		for (int i = 0; i < numOfUserRanges; i++) {
			for (int j = 0; j < sharedMemory.k; j++) {
				sendDataList.add(d_BAR_n_m[i][j]);
				sendDataList.add(t_BAR_n_m[i][j].toByteArray());
				sendDataList.add(t_BAR_dash_n_m[i][j].toByteArray());
				sendDataList.add(we_BAR_n_m[i][j].toByteArray());
				sendDataList.add(we_BAR_dash_n_m[i][j].toByteArray());

				sendDataList.add(wd_BAR_n_m[i][j].toByteArray());
				sendDataList.add(wd_BAR_dash_n_m[i][j].toByteArray());
			}
		}

		// add all the user policies to the list
		sendDataList.add(sharedMemory.stringToBytes(userData.P_U));

		// add the validity period of the user's credentials as well
		sendDataList.add(sharedMemory.stringToBytes(userData.VP_U));

		final ListData sendData = new ListData(sendDataList);
		return sendData.toBytes();
	}

	/**
	 * Generates the validator's random number.
	 *
	 * @return The validator's random number.
	 */
	private byte[] generateValidatorRandomNumber() {
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();
		final ValidatorData validatorData = (ValidatorData) sharedMemory.getData(Actor.VALIDATOR);
		// final Crypto crypto = Crypto.getInstance();

		// Select random r.
		final BigInteger r = crypto.secureRandom(sharedMemory.p);

		// Store part of the transcript r, saving any previous value.
		validatorData.r_last = validatorData.r;
		validatorData.r = r;

		// Send r
		final ListData sendData = new ListData(Arrays.asList(ValidatorData.ID_V, r.toByteArray()));
		return sendData.toBytes();
	}

	/**
	 * Verifies the returned seller's credential data.
	 *
	 * @return True if the verification is successful.
	 */
	private boolean verifySellerCredentials(byte[] data) {
		// final PPETSABCSharedMemory sharedMemory =
		// (PPETSABCSharedMemory)this.getSharedMemory();
		final SellerData sellerData = (SellerData) sharedMemory.getData(Actor.SELLER);
		final Crypto crypto = Crypto.getInstance();

		// Decode the received data.
		final ListData listData = ListData.fromBytes(data);

		if (listData.getList().size() != 4) {
			LOG.error("wrong number of data elements: " + listData.getList().size());
			return false;
		}

		final BigInteger c_s = new BigInteger(listData.getList().get(0));
		final BigInteger r_s = new BigInteger(listData.getList().get(1));
		final Element delta_S = sharedMemory.curveElementFromBytes(listData.getList().get(2));
		sellerData.VP_S = sharedMemory.stringFromBytes(listData.getList().get(3));

		// Verify e(delta_S, g_bar g^c_s) = e(g_0, g) e(Y_I, g) e(g, g_frac)^r_s
		final Element left = sharedMemory.pairing.pairing(delta_S, sharedMemory.g_bar.add(sharedMemory.g.mul(c_s)));

		final byte[] vpsHash = crypto.getHash(sellerData.VP_S.getBytes());
		final BigInteger vpsHashNum = new BigInteger(1, vpsHash).mod(sharedMemory.p);
		LOG.debug("vpsHashNum: " + vpsHashNum);

		final Element right1 = sharedMemory.pairing.pairing(sharedMemory.g_n[0], sharedMemory.g).getImmutable();
		final Element right2 = sharedMemory.pairing.pairing(sharedMemory.g_n[1], sharedMemory.g).pow(vpsHashNum)
				.getImmutable();
		final Element right3 = sharedMemory.pairing.pairing(sellerData.Y_S, sharedMemory.g).getImmutable();
		final Element right4 = sharedMemory.pairing.pairing(sharedMemory.g_frak, sharedMemory.g).pow(r_s)
				.getImmutable();

		final Element RHS = right1.mul(right2).mul(right3).mul(right4).getImmutable();
		if (!left.equals(RHS)) {
			LOG.error("invalid seller credentials");
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}
		LOG.debug("SUCCESS: passed verification of seller credentials");
		// Keep the credentials.
		sellerData.c_s = c_s;
		sellerData.r_s = r_s;
		sellerData.delta_S = delta_S;
		LOG.debug("Seller.delta_S=" + sellerData.delta_S);

		return true;
	}

	private boolean verifySellerProof(byte[] data) {
		// Note that all elliptic curve calculations are in an additive group
		// such that * -> + and ^ -> *.
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();
		// final Crypto crypto = Crypto.getInstance();

		// Decode the received data.
		final ListData listData = ListData.fromBytes(data);

		if (listData.getList().size() != 20) {
			LOG.error("wrong number of data elements: " + listData.getList().size());
			return false;
		}

		final Element M_2_S = sharedMemory.curveElementFromBytes(listData.getList().get(0));

		final Element Q = sharedMemory.curveElementFromBytes(listData.getList().get(1));
		final Element Z = sharedMemory.curveElementFromBytes(listData.getList().get(2));
		final Element gamma = sharedMemory.curveElementFromBytes(listData.getList().get(3));
		// Ignore Z_dash
		// Ignore gamma_dash
		final Element omega = sharedMemory.gtFiniteElementFromBytes(listData.getList().get(6));
		// Ignore omega_dash

		final byte[] c_bar_1 = listData.getList().get(8);
		final BigInteger c_bar_1Num = new BigInteger(1, c_bar_1).mod(sharedMemory.p);

		final byte[] c_bar_2 = listData.getList().get(9);
		final BigInteger c_bar_2Num = new BigInteger(1, c_bar_2).mod(sharedMemory.p);

		final byte[] c_bar_3 = listData.getList().get(10);
		final BigInteger c_bar_3Num = new BigInteger(1, c_bar_3).mod(sharedMemory.p);

		final BigInteger s_bar_1 = new BigInteger(listData.getList().get(11));
		final BigInteger s_bar_2 = new BigInteger(listData.getList().get(12));

		final BigInteger s_hat_1 = new BigInteger(listData.getList().get(13));
		final BigInteger s_hat_2 = new BigInteger(listData.getList().get(14));

		final BigInteger r_bar_1 = new BigInteger(listData.getList().get(15));
		final BigInteger r_bar_2 = new BigInteger(listData.getList().get(16));
		final BigInteger r_bar_3 = new BigInteger(listData.getList().get(17));
		final BigInteger r_bar_4 = new BigInteger(listData.getList().get(18));
		final BigInteger r_bar_5 = new BigInteger(listData.getList().get(19));

		// Verify c_bar_1 = H(M_2_S || Z || g^s_bar_1 * theta^s_bar_2 *
		// Z^c_bar_1)
		final Element check1 = sharedMemory.g.mul(s_bar_1).add(sharedMemory.theta.mul(s_bar_2)).add(Z.mul(c_bar_1Num));
		final ListData c_bar_1VerifyData = new ListData(Arrays.asList(M_2_S.toBytes(), Z.toBytes(), check1.toBytes()));
		final byte[] c_bar_1Verify = crypto.getHash(c_bar_1VerifyData.toBytes());

		if (!Arrays.equals(c_bar_1, c_bar_1Verify)) {
			LOG.error("failed to verify PI_2_S: c_bar_1");
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}
		LOG.debug("SUCCESS: passed verification of PI_2_S: c_bar_1");

		// Verify c_bar_2 = H(M_2_S || gamma || g^s_hat_1 * theta^s_hat_2 *
		// gamma^c_bar_2)
		final Element check2 = sharedMemory.g.mul(s_hat_1).add(sharedMemory.theta.mul(s_hat_2))
				.add(gamma.mul(c_bar_2Num));
		final ListData c_bar_2VerifyData = new ListData(
				Arrays.asList(M_2_S.toBytes(), gamma.toBytes(), check2.toBytes()));
		final byte[] c_bar_2Verify = crypto.getHash(c_bar_2VerifyData.toBytes());

		if (!Arrays.equals(c_bar_2, c_bar_2Verify)) {
			LOG.error("failed to verify PI_2_S: c_bar_2");
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}
		LOG.debug("SUCCESS: passed verification of PI_2_S: c_bar_2");
		// Verify c_bar_3 = H(M_2_S || omega || e(rho,g)^r_bar_1 *
		// e(g_frak,g)^r_bar_2 * e(Q,g)^-r_bar_3 * e(theta,g)^r_bar_4 * e
		// (theta,g_bar)^r_bar_5 * omega^c_bar_3)
		final Element check3_1 = sharedMemory.pairing.pairing(sharedMemory.rho, sharedMemory.g).pow(r_bar_1)
				.getImmutable();
		final Element check3_2 = sharedMemory.pairing.pairing(sharedMemory.g_frak, sharedMemory.g).pow(r_bar_2)
				.getImmutable();
		final Element check3_3 = sharedMemory.pairing.pairing(Q, sharedMemory.g)
				.pow(r_bar_3.negate().mod(sharedMemory.p)).getImmutable();
		final Element check3_4 = sharedMemory.pairing.pairing(sharedMemory.theta, sharedMemory.g).pow(r_bar_4)
				.getImmutable();
		final Element check3_5 = sharedMemory.pairing.pairing(sharedMemory.theta, sharedMemory.g_bar).pow(r_bar_5)
				.getImmutable();

		final Element check3_6 = omega.pow(c_bar_3Num).getImmutable();
		final Element check3 = check3_1.mul(check3_2).mul(check3_3).mul(check3_4).mul(check3_5).mul(check3_6)
				.getImmutable();

		final ListData c_bar_3VerifyData = new ListData(
				Arrays.asList(M_2_S.toBytes(), omega.toBytes(), check3.toBytes()));
		final byte[] c_bar_3Verify = crypto.getHash(c_bar_3VerifyData.toBytes());

		if (!Arrays.equals(c_bar_3, c_bar_3Verify)) {
			LOG.error("failed to verify PI_2_S: c_bar_3");
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}
		LOG.debug("SUCCESS: passed verification of PI_2_S: c_bar_3");
		return true;
	}

	/**
	 * Verifies the ticket proof.
	 *
	 * @param data
	 *            The data received from the user.
	 * @return True if the ticket proof is verified.
	 */
	private boolean verifyTicketProof(byte[] data) {
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();
		final ValidatorData validatorData = (ValidatorData) sharedMemory.getData(Actor.VALIDATOR);
		final Crypto crypto = Crypto.getInstance();

		// Decode the received data.
		final ListData listData = ListData.fromBytes(data);

		if (listData.getList().size() != 23) {
			LOG.error("wrong number of data elements: " + listData.getList().size());
			return false;
		}

		// Receive P_U, Price, Service, VP_T, M_3_U, D, Ps_U, E, F, J, J_dash, R, c,
		// s_BAR_u, x_BAR_u, s_hat_u, pi_BAR, lambda_BAR, omega_BAR_u, pi_BAR_dash,
		// d_BAR_u, psi_uNum
		// U also needs to send Y_I as the verifier won't have it otherwise

		int index = 0;
		final String P_U = sharedMemory.stringFromBytes(listData.getList().get(index++));
		final byte[] price = listData.getList().get(index++);
		final byte[] service = listData.getList().get(index++);
		final String VP_T = sharedMemory.stringFromBytes(listData.getList().get(index++));
		final Element M_3_U = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element D = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element Ps_U = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element E = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element F = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element J = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element J_dash = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element R = sharedMemory.gtFiniteElementFromBytes(listData.getList().get(index++));
		final byte[] c = listData.getList().get(index++);
		final BigInteger s_BAR_u = new BigInteger(listData.getList().get(index++));
		final BigInteger x_BAR_u = new BigInteger(listData.getList().get(index++));
		final BigInteger s_hat_u = new BigInteger(listData.getList().get(index++));
		final BigInteger pi_BAR = new BigInteger(listData.getList().get(index++));
		final BigInteger lambda_BAR = new BigInteger(listData.getList().get(index++));
		final BigInteger omega_BAR_u = new BigInteger(listData.getList().get(index++));
		final BigInteger pi_BAR_dash = new BigInteger(listData.getList().get(index++));
		final BigInteger d_BAR_u = new BigInteger(listData.getList().get(index++));
		final BigInteger psi_uNum = new BigInteger(listData.getList().get(index++));
		final Element Y_S = sharedMemory.curveElementFromBytes(listData.getList().get(index++));

		// Verify psi_uNum
		// Compute psi_u = H(P_U || Price || Service || Ticket Valid_Period)
		final ListData check_psi_uData = new ListData(
				Arrays.asList(sharedMemory.stringToBytes(P_U), price, service, sharedMemory.stringToBytes(VP_T)));
		final byte[] check_psi_u = crypto.getHash(check_psi_uData.toBytes());
		final BigInteger check_psi_uNum = new BigInteger(1, check_psi_u).mod(sharedMemory.p);

		if (!psi_uNum.equals(check_psi_uNum)) {
			LOG.error("failed to verify psi_uNum");
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}

		LOG.debug("SUCCESS: verified psi_uNum");

		// Verify R

		// R = e(F,Y_I) / (e(g_0,rho) e(Y,rho) e(g_3, rho)^psi_u
		// R_bar = e(xi,rho)^x_bar_u * e(g_1,rho)^d_bar_u * e(g_2,rho)^s_bar_u *
		// e(F,rho)^-omega_bar_u * e(theta,rho)^pi_bar_dash *
		// e(theta,rho)^pi_bar
		final Element checkR_1 = sharedMemory.pairing.pairing(F, Y_S);
		final Element checkR_2 = sharedMemory.pairing.pairing(sharedMemory.g_n[0], sharedMemory.rho).getImmutable();
		final Element checkR_3 = sharedMemory.pairing.pairing(Ps_U, sharedMemory.rho).getImmutable();

		final Element checkR_4 = sharedMemory.pairing.pairing(sharedMemory.g_n[3], sharedMemory.rho).pow(psi_uNum)
				.getImmutable();

		final Element checkR = checkR_1.div(checkR_2.mul(checkR_3).mul(checkR_4)).getImmutable();

		if (!R.equals(checkR)) {
			LOG.error("failed to verify R");
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}

		LOG.debug("SUCCESS: verified R");

		// Verify c.
		final BigInteger cNum = new BigInteger(1, c).mod(sharedMemory.p);
		final List<byte[]> cVerifyList = new ArrayList<>();
		cVerifyList.addAll(Arrays.asList(M_3_U.toBytes(), D.toBytes(), Ps_U.toBytes(), E.toBytes(), J.toBytes(),
				J_dash.toBytes(), R.toBytes()));

		// Verify D_bar
		final Element cCheck1 = sharedMemory.g.mul(s_BAR_u).add(D.mul(cNum));
		cVerifyList.add(cCheck1.toBytes());

		// verify Ps_bar_U
		final Element cCheck2 = sharedMemory.xi.mul(x_BAR_u).add(sharedMemory.g_n[1].mul(d_BAR_u)).add(Ps_U.mul(cNum));
		cVerifyList.add(cCheck2.toBytes());

		// Verify E_bar
		final byte[] hashID_V = crypto.getHash(ValidatorData.ID_V);
		final Element elementFromHashID_V = sharedMemory.pairing.getG1()
				.newElementFromHash(hashID_V, 0, hashID_V.length).getImmutable();

		final Element cCheck3 = sharedMemory.xi.mul(x_BAR_u).add(elementFromHashID_V.mul(s_hat_u)).add(E.mul(cNum));
		cVerifyList.add(cCheck3.toBytes());

		// Verify J_bar
		final Element cCheck4 = ((sharedMemory.g.mul(pi_BAR)).add(sharedMemory.theta.mul(lambda_BAR))).add(J.mul(cNum));
		cVerifyList.add(cCheck4.toBytes());

		// Verify J_bar_dash
		final Element cCheck5 = J.mul(omega_BAR_u).add(J_dash.mul(cNum));
		cVerifyList.add(cCheck5.toBytes());

		// verify R'
		final Element cCheck6_1 = sharedMemory.pairing.pairing(sharedMemory.g_n[2], sharedMemory.rho).pow(s_BAR_u);
		final Element cCheck6_2 = sharedMemory.pairing.pairing(F, sharedMemory.rho)
				.pow(omega_BAR_u.negate().mod(sharedMemory.p));
		final Element cCheck6_3 = sharedMemory.pairing.pairing(sharedMemory.theta, sharedMemory.rho).pow(pi_BAR_dash);
		final Element cCheck6_4 = sharedMemory.pairing.pairing(sharedMemory.theta, Y_S).pow(pi_BAR).mul(R.pow(cNum));
		final Element cCheck6 = cCheck6_1.mul(cCheck6_2).mul(cCheck6_3).mul(cCheck6_4);
		cVerifyList.add(cCheck6.toBytes());

		final ListData cVerifyData = new ListData(cVerifyList);
		final byte[] cVerify = crypto.getHash(cVerifyData.toBytes());

		if (!Arrays.equals(c, cVerify)) {
			LOG.error("failed to verify PI_3_U: c");
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}
		LOG.debug("SUCCESS: Verified PI_3_U");
		// Store the transcript ((r, D, E), F, J), saving any previous value.
		// r has already been stored when generated above.
		validatorData.D_last = validatorData.D;
		validatorData.E_last = validatorData.E;
		validatorData.F_last = validatorData.F;
		validatorData.J_last = validatorData.J;

		validatorData.D = D;
		validatorData.E = E;
		validatorData.F = F;
		validatorData.J = J;

		return true;
	}

	private boolean verifyTicketSerialNumber(byte[] data) {
		// Note that all elliptic curve calculations are in an additive group
		// such that * -> + and ^ -> *.
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();
		final UserData userData = (UserData) sharedMemory.getData(Actor.USER);

		// Decode the received data.
		final ListData listData = ListData.fromBytes(data);

		if (listData.getList().size() != 9) {
			LOG.error("wrong number of data elements: " + listData.getList().size());
			return false;
		}

		final Element T_U = sharedMemory.curveElementFromBytes(listData.getList().get(0));
		final BigInteger d_dash = new BigInteger(listData.getList().get(1));
		final BigInteger s_u = new BigInteger(listData.getList().get(2));
		final BigInteger omega_u = new BigInteger(listData.getList().get(3));
		final BigInteger psi_uNum = new BigInteger(listData.getList().get(4));
		final Element Y_S = sharedMemory.curveElementFromBytes(listData.getList().get(5));
		final byte[] service = listData.getList().get(6);
		final byte[] price = listData.getList().get(7);
		final String VP_T = sharedMemory.stringFromBytes(listData.getList().get(8));

		// Compute d_u = d + d_dash
		final BigInteger d_u = userData.d.add(d_dash);

		// Check that e(T_U, Y_I * rho^omega_u) =? e(g_0,rho) * e(Y,rho) *
		// e(g_1,rho)^d_u * e(g_2,rho)^s_u
		final Element left = sharedMemory.pairing.pairing(T_U, Y_S.add(sharedMemory.rho.mul(omega_u))).getImmutable();

		final Element right1 = sharedMemory.pairing.pairing(sharedMemory.g_n[0], sharedMemory.rho).getImmutable();
		final Element right2 = sharedMemory.pairing.pairing(userData.Y_U, sharedMemory.rho).getImmutable();
		final Element right3 = sharedMemory.pairing.pairing(sharedMemory.g_n[1], sharedMemory.rho).pow(d_u)
				.getImmutable();
		final Element right4 = sharedMemory.pairing.pairing(sharedMemory.g_n[2], sharedMemory.rho).pow(s_u)
				.getImmutable();
		final Element right5 = sharedMemory.pairing.pairing(sharedMemory.g_n[3], sharedMemory.rho).pow(psi_uNum)
				.getImmutable();

		if (!left.isEqual(right1.mul(right2).mul(right3).mul(right4).mul(right5))) {
			LOG.error("failed to verify e(T_U, Y_I * rho^omega_u)");
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}

		// Keep the ticket Ticket_U = (d_u, d_dash, s_u, omega_u, T_U,
		// Time, Service, Price, Valid_Period).

		userData.d_u = d_u;
		userData.d_dash = d_dash;
		userData.s_u = s_u;
		userData.omega_u = omega_u;
		userData.T_U = T_U.getImmutable();
		userData.Y_S = Y_S.getImmutable();
		userData.service = service;
		userData.price = price;
		userData.VP_T = VP_T;
		userData.psi_uNum = psi_uNum;

		LOG.debug("SUCCESS: verified Ticket serial number");

		return true;
	}

	/**
	 * Verifies the returned user's credential data.
	 *
	 * @return True if the verification is successful.
	 */
	private boolean verifyUserCredentials(byte[] data) {
		// Note that all elliptic curve calculations are in an additive group such that
		// * -> + and ^ -> *.
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();
		final UserData userData = (UserData) sharedMemory.getData(Actor.USER);
		final Crypto crypto = Crypto.getInstance();

		// Decode the received data.
		final ListData listData = ListData.fromBytes(data);

		if (listData.getList().size() != 4) {
			LOG.error("wrong number of data elements: " + listData.getList().size());
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}

		final BigInteger c_u = new BigInteger(listData.getList().get(0));
		final BigInteger r_dash = new BigInteger(listData.getList().get(1));
		final Element delta_U = sharedMemory.curveElementFromBytes(listData.getList().get(2));
		userData.VP_U = sharedMemory.stringFromBytes(listData.getList().get(3));

		// Compute r_u.
		final BigInteger r_u = userData.r.add(r_dash).mod(sharedMemory.p);

		// Verify e(delta_U, g_bar g^c_u) = e(g_0, g) e(g_0,g_1)^H(VP_U) e(Y_U, g)
		// e(g_frac, g)^r_u
		final Element left = sharedMemory.pairing.pairing(delta_U, sharedMemory.g_bar.add(sharedMemory.g.mul(c_u)));
		final Element right1 = sharedMemory.pairing.pairing(sharedMemory.g_n[0], sharedMemory.g).getImmutable();

		final byte[] vpuHash = crypto.getHash(userData.VP_U.getBytes());
		final BigInteger vpuHashNum = new BigInteger(1, vpuHash).mod(sharedMemory.p);
		final Element right2 = sharedMemory.pairing.pairing(sharedMemory.g_n[1], sharedMemory.g).pow(vpuHashNum)
				.getImmutable();
		final Element right3 = sharedMemory.pairing.pairing(userData.Y_U, sharedMemory.g).getImmutable();
		final Element right4 = sharedMemory.pairing.pairing(sharedMemory.g_frak, sharedMemory.g).pow(r_u)
				.getImmutable();
		Element product1 = sharedMemory.pairing.getGT().newOneElement().getImmutable();
		for (int i = 0; i < UserData.A_U_range.length; i++) {
			final Element value = sharedMemory.pairing.pairing(sharedMemory.g_hat_n[i], sharedMemory.g)
					.pow(UserData.A_U_range[i]).getImmutable();
			product1 = product1.mul(value);
		}
		product1 = product1.getImmutable();

		Element product2 = sharedMemory.pairing.getGT().newOneElement().getImmutable();
		for (int i = 0; i < UserData.A_U_set.length; i++) {
			final byte[] hash = crypto.getHash(UserData.A_U_set[i].getBytes(Data.UTF8));
			final BigInteger hashNum = new BigInteger(1, hash).mod(sharedMemory.p);
			final Element value = sharedMemory.pairing.pairing(sharedMemory.eta_n[i], sharedMemory.g).pow(hashNum)
					.getImmutable();
			product2 = product2.mul(value);
		}

		final Element RHS = right1.mul(right2).mul(right3).mul(right4).mul(product1).mul(product2);
		if (!left.isEqual(RHS)) {
			LOG.error("invalid user credentials");
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}
		LOG.debug("SUCCESS: Verified user credentials:...");
		// Keep the credentials.
		userData.c_u = c_u;
		userData.r_u = r_u;
		userData.delta_U = delta_U;
		return true;
	}

	/**
	 * Verifies the user proof.
	 *
	 * @param data
	 *            The data received from the user.
	 * @return True if verified.
	 */
	private boolean verifyUserProof(byte[] data) {
		// Note that all elliptic curve calculations are in an additive group
		// such that * -> + and ^ -> *.
		// final PPETSABCSharedMemory sharedMemory = (PPETSABCSharedMemory)
		// this.getSharedMemory();
		final SellerData sellerData = (SellerData) sharedMemory.getData(Actor.SELLER);
		// final Crypto crypto = Crypto.getInstance();

		// Decode the received data.
		long timing = Instant.now().toEpochMilli();
		final ListData listData = ListData.fromBytes(data);

		if (listData.getList().size() <= 0) { // Way too many to go and count.
			LOG.error("wrong number of data elements: " + listData.getList().size());
			return false;
		}

		int index = 0;
		final Element M_2_U = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element C = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element D = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element phi = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		final Element Y = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
		// Save off Y so that we can compute the ticket serial number later
		sellerData.Y = Y; // the user pseudonym
		final Element R = sharedMemory.gtFiniteElementFromBytes(listData.getList().get(index++));

		final int numOfUserRanges = (new BigInteger(listData.getList().get(index++))).intValue();
		final int numOfUserSets = (new BigInteger(listData.getList().get(index++))).intValue();
		// LOG.debug("numOfUserRanges= "+numOfUserRanges);
		// LOG.debug("numOfUserSets= "+numOfUserSets);

		final Element[] Z_n = new Element[numOfUserRanges];
		final Element[] Z_dash_n = new Element[numOfUserRanges];
		final Element[] Z_bar_n = new Element[numOfUserRanges];
		final Element[] Z_bar_dash_n = new Element[numOfUserRanges];
		final Element[][] A_n_m = new Element[numOfUserRanges][sharedMemory.k];
		final Element[][] A_dash_n_m = new Element[numOfUserRanges][sharedMemory.k];
		final Element[][] V_n_m = new Element[numOfUserRanges][sharedMemory.k];
		final Element[][] V_bar_n_m = new Element[numOfUserRanges][sharedMemory.k];
		final Element[][] V_dash_n_m = new Element[numOfUserRanges][sharedMemory.k];
		final Element[][] V_bar_dash_n_m = new Element[numOfUserRanges][sharedMemory.k];

		for (int i = 0; i < numOfUserRanges; i++) {
			Z_n[i] = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
			Z_dash_n[i] = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
			Z_bar_n[i] = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
			Z_bar_dash_n[i] = sharedMemory.curveElementFromBytes(listData.getList().get(index++));

			for (int j = 0; j < sharedMemory.k; j++) {
				A_n_m[i][j] = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
				A_dash_n_m[i][j] = sharedMemory.curveElementFromBytes(listData.getList().get(index++));

				V_n_m[i][j] = sharedMemory.gtFiniteElementFromBytes(listData.getList().get(index++));
				V_bar_n_m[i][j] = sharedMemory.gtFiniteElementFromBytes(listData.getList().get(index++));
				V_dash_n_m[i][j] = sharedMemory.gtFiniteElementFromBytes(listData.getList().get(index++));
				V_bar_dash_n_m[i][j] = sharedMemory.gtFiniteElementFromBytes(listData.getList().get(index++));
			}
		}

		final Element[][] B_n_m = new Element[numOfUserSets][sharedMemory.biggestSetSize];
		final Element[][] W_n_m = new Element[numOfUserSets][sharedMemory.biggestSetSize];
		final Element[][] W_bar_n_m = new Element[numOfUserSets][sharedMemory.biggestSetSize];

		for (int i = 0; i < numOfUserSets; i++) {
			for (int j = 0; j < sharedMemory.biggestSetSize; j++) {
				B_n_m[i][j] = sharedMemory.curveElementFromBytes(listData.getList().get(index++));
				W_n_m[i][j] = sharedMemory.gtFiniteElementFromBytes(listData.getList().get(index++));
				W_bar_n_m[i][j] = sharedMemory.gtFiniteElementFromBytes(listData.getList().get(index++));
			}
		}

		final byte[] c_BAR = listData.getList().get(index++);
		final BigInteger c_BARNum = new BigInteger(1, c_BAR).mod(sharedMemory.p);
		final BigInteger c_BAR_u = new BigInteger(listData.getList().get(index++));
		final BigInteger x_BAR_u = new BigInteger(listData.getList().get(index++));
		final BigInteger d_BAR = new BigInteger(listData.getList().get(index++));
		final BigInteger r_BAR_u = new BigInteger(listData.getList().get(index++));
		final BigInteger alpha_BAR = new BigInteger(listData.getList().get(index++));
		final BigInteger beta_BAR = new BigInteger(listData.getList().get(index++));
		final BigInteger alpha_BAR_dash = new BigInteger(listData.getList().get(index++));
		final BigInteger beta_BAR_dash = new BigInteger(listData.getList().get(index++));

		final byte[][] e_BAR_m = new byte[numOfUserRanges][];
		final BigInteger[] e_BAR_mNum = new BigInteger[numOfUserRanges];
		final BigInteger[] gammac_BAR_n = new BigInteger[numOfUserRanges];
		final BigInteger[] ac_BAR_n = new BigInteger[numOfUserRanges];
		final BigInteger[] gammae_BAR_n = new BigInteger[numOfUserRanges];
		final BigInteger[] ae_BAR_n = new BigInteger[numOfUserRanges];
		final BigInteger[] ae_BAR_dash_n = new BigInteger[numOfUserRanges];

		for (int i = 0; i < numOfUserRanges; i++) {
			e_BAR_m[i] = listData.getList().get(index++);
			e_BAR_mNum[i] = new BigInteger(1, e_BAR_m[i]).mod(sharedMemory.p);

			gammac_BAR_n[i] = new BigInteger(listData.getList().get(index++));
			ac_BAR_n[i] = new BigInteger(listData.getList().get(index++));

			gammae_BAR_n[i] = new BigInteger(listData.getList().get(index++));
			ae_BAR_n[i] = new BigInteger(listData.getList().get(index++));
			ae_BAR_dash_n[i] = new BigInteger(listData.getList().get(index++));
		}

		final BigInteger[] e_BAR_n = new BigInteger[numOfUserSets];
		final BigInteger[] e_BAR_nNum = new BigInteger[numOfUserSets];
		final BigInteger[] e_BAR_dash_n = new BigInteger[numOfUserSets];
		final BigInteger[] e_BAR_dash_dash_n = new BigInteger[numOfUserSets];

		for (int i = 0; i < numOfUserSets; i++) {
			e_BAR_n[i] = new BigInteger(listData.getList().get(index++));
			e_BAR_nNum[i] = e_BAR_dash_n[i] = new BigInteger(listData.getList().get(index++));
			e_BAR_dash_dash_n[i] = new BigInteger(listData.getList().get(index++));
		}

		final byte[][][] d_BAR_n_m = new byte[numOfUserRanges][sharedMemory.k][];
		final BigInteger[][] d_BAR_n_mNum = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] t_BAR_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] t_BAR_dash_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] we_BAR_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] we_BAR_dash_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] wd_BAR_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];
		final BigInteger[][] wd_BAR_dash_n_m = new BigInteger[numOfUserRanges][sharedMemory.k];

		for (int i = 0; i < numOfUserRanges; i++) {
			for (int j = 0; j < sharedMemory.k; j++) {
				d_BAR_n_m[i][j] = listData.getList().get(index++);
				// Convert the hash to a number
				d_BAR_n_mNum[i][j] = new BigInteger(1, d_BAR_n_m[i][j]).mod(sharedMemory.p);
				t_BAR_n_m[i][j] = new BigInteger(listData.getList().get(index++));
				t_BAR_dash_n_m[i][j] = new BigInteger(listData.getList().get(index++));
				we_BAR_n_m[i][j] = new BigInteger(listData.getList().get(index++));
				we_BAR_dash_n_m[i][j] = new BigInteger(listData.getList().get(index++));
				wd_BAR_n_m[i][j] = new BigInteger(listData.getList().get(index++));
				wd_BAR_dash_n_m[i][j] = new BigInteger(listData.getList().get(index++));
			}
		}
		// get the user policy membership and store them for later
		sellerData.U_membershipDetails = sharedMemory.stringFromBytes(listData.getList().get(index++));

		// get the user's validity period
		final String VP_U = sharedMemory.stringFromBytes(listData.getList().get(index++));

		timing = Instant.now().toEpochMilli() - timing;
		LOG.debug("Decoding data took (ms)= " + timing);

		// timing of checking R
		timing = Instant.now().toEpochMilli();
		// first check that the VP_U was used correctly in the computation of R
		final byte[] vpuHash = crypto.getHash(VP_U.getBytes());
		final BigInteger vpuHashNum = new BigInteger(1, vpuHash).mod(sharedMemory.p);

		final Element checkR = sharedMemory.pairing.pairing(C, sharedMemory.g_bar)
				.div(sharedMemory.pairing.pairing(sharedMemory.g_n[0], sharedMemory.g)
						.mul(sharedMemory.pairing.pairing(sharedMemory.g_n[1], sharedMemory.g).pow(vpuHashNum)))
				.getImmutable();

		if (!R.isEqual(checkR)) {
			LOG.error("failed to verify VP_U usage in computing R");
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}
		LOG.debug("SUCCESS: passed verification of PI_2_U: R");
		timing = Instant.now().toEpochMilli() - timing;
		LOG.debug("checking R took (ms): " + timing);

		// timing of checking c_BAR
		timing = Instant.now().toEpochMilli();

		// Verify c_BAR.
		final List<byte[]> c_BARVerifyList = new ArrayList<>();
		c_BARVerifyList.addAll(Arrays.asList(M_2_U.toBytes(), Y.toBytes()));

		// check Y_bar
		final Element c_BARCheck1 = (sharedMemory.xi.mul(x_BAR_u)).add(sharedMemory.g_n[1].mul(d_BAR))
				.add(Y.mul(c_BARNum)).getImmutable();
		// LOG.debug("c_BARCheck1=Y_bar= "+c_BARCheck1);

		c_BARVerifyList.add(c_BARCheck1.toBytes());

		c_BARVerifyList.add(D.toBytes());

		// check D_bar
		final Element c_BARCheck2 = sharedMemory.g.mul(alpha_BAR).add(sharedMemory.theta.mul(beta_BAR))
				.add(D.mul(c_BARNum)).getImmutable();
		c_BARVerifyList.add(c_BARCheck2.toBytes());
		// LOG.debug("c_Barcheck2=D_bar="+c_BARCheck2);

		c_BARVerifyList.add(phi.toBytes());

		final Element c_BARCheck3 = sharedMemory.g.mul(alpha_BAR_dash).add(sharedMemory.theta.mul(beta_BAR_dash))
				.add(phi.mul(c_BARNum));
		c_BARVerifyList.add(c_BARCheck3.toBytes());
		// LOG.debug("c_Barcheck3=phi_bar="+c_BARCheck3);

		c_BARVerifyList.add(C.toBytes());
		c_BARVerifyList.add(R.toBytes());

		// the following computations should produce R_dash
		Element R_dash1 = sharedMemory.pairing.pairing(sharedMemory.xi, sharedMemory.g).pow(x_BAR_u).getImmutable();
		Element R_dash2 = sharedMemory.pairing.pairing(sharedMemory.g_frak, sharedMemory.g).pow(r_BAR_u).getImmutable();
		Element R_dash3 = sharedMemory.pairing.getGT().newOneElement();
		// part 1 of range verification
		long rangeVerificationTiming = Instant.now().toEpochMilli();
		LOG.debug("rangeVerification (part 1 start) so far: " + rangeVerificationTiming);

		for (int i = 0; i < numOfUserRanges; i++) {
			Element value = sharedMemory.pairing.pairing(sharedMemory.g_hat_n[i], sharedMemory.g).pow(ac_BAR_n[i])
					.getImmutable();
			R_dash3 = R_dash3.mul(value);
		}
		// end of part 1 of range verification
		rangeVerificationTiming = Instant.now().toEpochMilli() - rangeVerificationTiming;
		LOG.debug("rangeVerification (part 1 end) so far: " + rangeVerificationTiming);

		// part 1 of set verification
		long setVerificationTiming = Instant.now().toEpochMilli();
		LOG.debug("setVerification (part 1 start) so far: " + setVerificationTiming);
		Element R_dash4 = sharedMemory.pairing.getGT().newOneElement();
		for (int i = 0; i < numOfUserSets; i++) {
			Element value = sharedMemory.pairing.pairing(sharedMemory.eta_n[i], sharedMemory.g).pow(e_BAR_dash_n[i])
					.getImmutable();
			R_dash4 = R_dash4.mul(value);
		}
		// end of part 1 of set verification
		setVerificationTiming = Instant.now().toEpochMilli() - setVerificationTiming;
		LOG.debug("setVerification (part 1 end) so far: " + setVerificationTiming);

		Element R_dash5 = sharedMemory.pairing.pairing(C, sharedMemory.g).pow(c_BAR_u.negate().mod(sharedMemory.p))
				.getImmutable();
		Element R_dash6 = sharedMemory.pairing.pairing(sharedMemory.theta, sharedMemory.g).pow(alpha_BAR_dash)
				.getImmutable();
		Element R_dash7 = sharedMemory.pairing.pairing(sharedMemory.theta, sharedMemory.g_bar).pow(alpha_BAR)
				.getImmutable();
		Element R_dash8 = R.pow(c_BARNum).getImmutable();
		Element R_dash = R_dash1.mul(R_dash2).mul(R_dash3).mul(R_dash4).mul(R_dash5).mul(R_dash6).mul(R_dash7)
				.mul(R_dash8).getImmutable();

		// LOG.debug("R_dash_verify="+R_dash);

		c_BARVerifyList.add(R_dash.toBytes());

		// LOG.debug("Initial: Verify Hash so far:
		// "+base64.encodeToString(crypto.getHash((new
		// ListData(c_BARVerifyList)).toBytes())));

		// part 2 of range verification
		rangeVerificationTiming = rangeVerificationTiming - Instant.now().toEpochMilli();
		LOG.debug("rangeVerification (part 2 start) so far: " + rangeVerificationTiming);

		for (int i = 0; i < numOfUserRanges; i++) {
			c_BARVerifyList.add(Z_n[i].toBytes());
		}
		// LOG.debug("Z_n: Verify Hash so far:
		// "+base64.encodeToString(crypto.getHash((new
		// ListData(c_BARVerifyList)).toBytes())));

		for (int i = 0; i < numOfUserRanges; i++) {
			final Element c_BARCheck4 = sharedMemory.g.mul(gammac_BAR_n[i]).add(sharedMemory.h.mul(ac_BAR_n[i]))
					.add(Z_n[i].mul(c_BARNum));
			// LOG.debug("verify Z_dash_n["+i+"]= "+ c_BARCheck4);
			c_BARVerifyList.add(c_BARCheck4.toBytes());
		}

		// LOG.debug("Z_dash_n: Verify Hash so far:
		// "+base64.encodeToString(crypto.getHash((new
		// ListData(c_BARVerifyList)).toBytes())));

		// end of part 2 of range verification
		rangeVerificationTiming = rangeVerificationTiming + Instant.now().toEpochMilli();
		LOG.debug("rangeVerification (part 2 end) so far: " + rangeVerificationTiming);

		// part 2 of set verification
		setVerificationTiming = setVerificationTiming - Instant.now().toEpochMilli();
		LOG.debug("setVerification (part 2 start) so far: " + setVerificationTiming);

		for (int i = 0; i < numOfUserSets; i++) {
			for (int j = 0; j < sharedMemory.biggestSetSize; j++) {
				c_BARVerifyList.add(B_n_m[i][j].toBytes());
			}
		}
		// LOG.debug("B_n_m: Verify Hash so far:
		// "+base64.encodeToString(crypto.getHash((new
		// ListData(c_BARVerifyList)).toBytes())));

		for (int i = 0; i < numOfUserSets; i++) {
			for (int j = 0; j < sharedMemory.biggestSetSize; j++) {
				// LOG.debug("W_n_m["+i+"]["+j+"] = "+W_n_m[i][j]);
				c_BARVerifyList.add(W_n_m[i][j].toBytes());
			}
		}

		// LOG.debug("W_n_m: Verify Hash so far:
		// "+base64.encodeToString(crypto.getHash((new
		// ListData(c_BARVerifyList)).toBytes())));

		for (int i = 0; i < numOfUserSets; i++) {
			final int currentSetSize = sharedMemory.zeta(i);
			for (int j = 0; j < sharedMemory.biggestSetSize; j++) {
				if ((j < currentSetSize) && UserData.A_U_set[i].equalsIgnoreCase(sharedMemory.setPolices[i][j])) {
					Element product2 = sharedMemory.pairing.pairing(sharedMemory.eta, sharedMemory.eta_n[i])
							.pow(e_BAR_n[i]).getImmutable();
					product2 = product2.mul(
							sharedMemory.pairing.pairing(B_n_m[i][j], sharedMemory.eta_n[i]).pow(e_BAR_dash_dash_n[i]))
							.getImmutable();
					product2 = product2.mul(W_n_m[i][j].pow(c_BARNum)).getImmutable();
					// LOG.debug("W_bar_check["+i+"]["+j+"] = "+product2);
					c_BARVerifyList.add(product2.toBytes());
				} else {
					// just stick some random but fixed element here as it is not used...
					c_BARVerifyList.add(sharedMemory.gt.toBytes());
				}
			}
		}
		// end of part 2 of set verification
		setVerificationTiming = setVerificationTiming + Instant.now().toEpochMilli();
		LOG.debug("setVerification (part 2 end) so far: " + setVerificationTiming);

		final ListData c_BARVerifyData = new ListData(c_BARVerifyList);
		final byte[] c_BARVerify = crypto.getHash(c_BARVerifyData.toBytes());
		// LOG.debug("c_BAR="+base64.encodeToString(c_BAR));
		// LOG.debug("c_BARVerify="+base64.encodeToString(c_BARVerify));

		if (!Arrays.equals(c_BAR, c_BARVerify)) {
			LOG.error("failed to verify PI_2_U: c_BAR");
			if (!sharedMemory.skipVerification) {
				return false;
			}
		}

		LOG.debug("SUCCESS: verified user proof: PI_2_U: c_BAR");
		timing = Instant.now().toEpochMilli() - timing;
		LOG.debug("checking c_bar took (ms): " + timing);

		// part 3 of range verification
		rangeVerificationTiming = rangeVerificationTiming - Instant.now().toEpochMilli();
		LOG.debug("rangeVerification (part 3 start) so far: " + rangeVerificationTiming);

		// timing e_BAR_m
		timing = Instant.now().toEpochMilli();
		// Verify e_BAR_m.
		for (int i = 0; i < numOfUserRanges; i++) {
			final BigInteger lower = BigInteger.valueOf(sharedMemory.rangePolicies[i][0]);
			final BigInteger upper = BigInteger.valueOf(sharedMemory.rangePolicies[i][1]);

			final List<byte[]> e_BAR_mVerifyList = new ArrayList<>();
			e_BAR_mVerifyList.addAll(Arrays.asList(M_2_U.toBytes(), Z_n[i].toBytes()));
			Element e_BAR_mVerifyCheck1a = sharedMemory.g.mul(gammae_BAR_n[i]).getImmutable();
			Element e_BAR_mVerifyCheck1b = sharedMemory.h.mul(ae_BAR_n[i]).getImmutable();
			Element e_BAR_mVerifyCheck1c = (Z_n[i].add(sharedMemory.h.mul(lower.negate().mod(sharedMemory.p))))
					.mul(e_BAR_mNum[i]).getImmutable();
			Element e_BAR_mVerifyCheck1 = e_BAR_mVerifyCheck1a.add(e_BAR_mVerifyCheck1b).add(e_BAR_mVerifyCheck1c)
					.getImmutable();
			e_BAR_mVerifyList.add(e_BAR_mVerifyCheck1.toBytes());

			Element e_BAR_mVerifyCheck2a = sharedMemory.g.mul(gammae_BAR_n[i]).getImmutable();
			Element e_BAR_mVerifyCheck2b = sharedMemory.pairing.getG1().newZeroElement();
			for (int j = 0; j < sharedMemory.k; j++) {
				final Element e_BAR_mVerifyCheck2b_j = sharedMemory.h_bar_n[j].mul(we_BAR_n_m[i][j]).getImmutable();
				e_BAR_mVerifyCheck2b.add(e_BAR_mVerifyCheck2b_j);
			}
			final Element e_BAR_mVerifyCheck2c = (Z_n[i].add(sharedMemory.h.mul(lower.negate().mod(sharedMemory.p))))
					.mul(e_BAR_mNum[i]).getImmutable();
			final Element e_BAR_mVerifyCheck2 = e_BAR_mVerifyCheck2a.add(e_BAR_mVerifyCheck2b)
					.add(e_BAR_mVerifyCheck2c);
			e_BAR_mVerifyList.add(e_BAR_mVerifyCheck2.toBytes());

			final BigInteger limit = BigInteger.valueOf((long) Math.pow(sharedMemory.q, sharedMemory.k));
			final Element e_BAR_mVerifyCheck3a = sharedMemory.g.mul(gammae_BAR_n[i]).getImmutable();

			Element e_BAR_mVerifyCheck3b = sharedMemory.pairing.getG1().newZeroElement().getImmutable();
			for (int j = 0; j < sharedMemory.k; j++) {
				e_BAR_mVerifyCheck3b = e_BAR_mVerifyCheck3b.add(sharedMemory.h_bar_n[j].mul(we_BAR_dash_n_m[i][j]))
						.getImmutable();
			}
			Element e_BAR_mVerifyCheck3c = sharedMemory.h.mul(limit.subtract(upper)).getImmutable();
			e_BAR_mVerifyCheck3c = e_BAR_mVerifyCheck3c.add(Z_n[i]).mul(e_BAR_mNum[i]);

			final Element e_BAR_mVerifyCheck3 = e_BAR_mVerifyCheck3a.add(e_BAR_mVerifyCheck3b)
					.add(e_BAR_mVerifyCheck3c);

			e_BAR_mVerifyList.add(e_BAR_mVerifyCheck3.toBytes());

			final ListData e_BAR_mVerifyData = new ListData(e_BAR_mVerifyList);
			final byte[] e_BAR_mVerify = crypto.getHash(e_BAR_mVerifyData.toBytes());

			if (!Arrays.equals(e_BAR_m[i], e_BAR_mVerify)) {
				LOG.error("failed to verify PI_2_U: e_BAR_n: " + i);
				if (!sharedMemory.skipVerification) {
					return false;
				}
			}
		}
		LOG.debug("SUCCESS: verified PI_2_U: e_BAR_n");
		timing = Instant.now().toEpochMilli() - timing;
		LOG.debug("checking e_BAR_m took (ms): " + timing);

		timing = Instant.now().toEpochMilli();
		// Verify d_BAR_n_m
		for (int i = 0; i < numOfUserRanges; i++) {
			for (int j = 0; j < sharedMemory.k; j++) {
				final List<byte[]> d_BAR_n_mVerifyList = new ArrayList<>();
				d_BAR_n_mVerifyList.addAll(Arrays.asList(M_2_U.toBytes(), A_n_m[i][j].toBytes(),
						A_dash_n_m[i][j].toBytes(), V_n_m[i][j].toBytes(), V_dash_n_m[i][j].toBytes()));

				Element d_BAR_n_mVerifyCheck1a = sharedMemory.pairing.pairing(sharedMemory.h, sharedMemory.h)
						.pow(t_BAR_n_m[i][j]).getImmutable();
				Element d_BAR_n_mVerifyCheck1b = sharedMemory.pairing.pairing(A_n_m[i][j], sharedMemory.h)
						.pow(wd_BAR_n_m[i][j].negate().mod(sharedMemory.p)).getImmutable();
				Element d_BAR_n_mVerifyCheck1c = V_n_m[i][j].pow(d_BAR_n_mNum[i][j]);
				Element d_BAR_n_mVerifyCheck1 = d_BAR_n_mVerifyCheck1a.mul(d_BAR_n_mVerifyCheck1b)
						.mul(d_BAR_n_mVerifyCheck1c).getImmutable();

				d_BAR_n_mVerifyList.add(d_BAR_n_mVerifyCheck1.toBytes());

				Element d_BAR_n_mVerifyCheck2a = sharedMemory.pairing.pairing(sharedMemory.h, sharedMemory.h)
						.pow(t_BAR_dash_n_m[i][j]).getImmutable();
				Element d_BAR_n_mVerifyCheck2b = sharedMemory.pairing.pairing(A_dash_n_m[i][j], sharedMemory.h)
						.pow(wd_BAR_dash_n_m[i][j].negate().mod(sharedMemory.p)).getImmutable();
				Element d_BAR_n_mVerifyCheck2c = V_dash_n_m[i][j].pow(d_BAR_n_mNum[i][j]);
				Element d_BAR_n_mVerifyCheck2 = d_BAR_n_mVerifyCheck2a.mul(d_BAR_n_mVerifyCheck2b)
						.mul(d_BAR_n_mVerifyCheck2c).getImmutable();
				d_BAR_n_mVerifyList.add(d_BAR_n_mVerifyCheck2.toBytes());

				final ListData d_BAR_n_mVerifyData = new ListData(d_BAR_n_mVerifyList);
				final byte[] d_BAR_n_mVerify = crypto.getHash(d_BAR_n_mVerifyData.toBytes());

				if (!Arrays.equals(d_BAR_n_m[i][j], d_BAR_n_mVerify)) {
					LOG.error("failed to verify PI_2_U: d_BAR_n_m: " + i + ", " + j);
					if (!sharedMemory.skipVerification) {
						return false;
					}
				}
			}
		}
		// end of part 3 of range verification
		rangeVerificationTiming = rangeVerificationTiming + Instant.now().toEpochMilli();
		LOG.debug("rangeVerification so far: " + rangeVerificationTiming);

		LOG.debug("SUCCESS: verified PI_2_U: d_BAR_n_m");
		timing = Instant.now().toEpochMilli() - timing;
		LOG.debug("checking d_BAR_n_m took (ms): " + timing);

		addTimings("RangeSetProof-0030: Range proof verification", rangeVerificationTiming);
		LOG.debug("***************************************************************************************");
		LOG.debug("Total timing for the range verification (ms): " + rangeVerificationTiming);
		LOG.debug("which involved N1 ranges where N1= " + numOfUserRanges);
		LOG.debug("***************************************************************************************");
		addTimings("RangeSetProof-0060: Set proof verification", setVerificationTiming);
		LOG.debug("***************************************************************************************");
		LOG.debug("Total timing for the set verification (ms): " + setVerificationTiming);
		LOG.debug("which involved N2 sets where N2= " + numOfUserSets);
		LOG.debug("***************************************************************************************");

		return true;
	}

}