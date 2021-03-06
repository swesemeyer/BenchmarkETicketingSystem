/**
 * DICE NFC evaluation.
 *
 * (c) University of Surrey and Pervasive Intelligence Ltd 2017.
 */
package uk.ac.surrey.bets_framework.protocol.ppetsabc_lite;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.surrey.bets_framework.protocol.NFCReaderStateMachine;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.PPETSABCIssuingStates;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.PPETSABCRegistrationStates;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.PPETSABCSetupStates;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.PPETSABCSharedMemory;
import uk.ac.surrey.bets_framework.protocol.ppetsabc.PPETSABCSharedMemory.PairingType;
import uk.ac.surrey.bets_framework.state.SharedMemory;

/**
 * Implements the revised, lighter version of the PPETS-ABC (Han, unpublished) NFC protocol as a state machine.
 *
 * Han, J., Chen, L., Schneider, S. & Treharne, H. (unpublished).
 * "PPETS-ABC: Privacy-preserving Electronic Ticket Scheme with Attribute-based Credentials".
 *
 * @author Matthew Casey
 */
public class PPETSABCLite extends NFCReaderStateMachine {

  /** Logback logger. */
  private static final Logger  LOG          = LoggerFactory.getLogger(PPETSABCLite.class);

  /** The shared memory. */
  private PPETSABCSharedMemory sharedMemory = new PPETSABCSharedMemory();

  /**
   * Default constructor.
   */
  public PPETSABCLite() {
    // Note that some states are modified from the non-lite version.
    super(Arrays.asList(new PPETSABCSetupStates.SState00(), new PPETSABCSetupStates.SState01(), new PPETSABCSetupStates.SState02(),
        new PPETSABCSetupStates.SState03(), new PPETSABCRegistrationStates.RState04(), new PPETSABCRegistrationStates.RState05(),
        new PPETSABCRegistrationStates.RState06(), new PPETSABCRegistrationStates.RState07(),
        new PPETSABCLiteIssuingStates.ImState08(), new PPETSABCIssuingStates.IState09(), new PPETSABCIssuingStates.IState10(),
        new PPETSABCLiteValidationStates.VState11(), new PPETSABCLiteValidationStates.VState12()));
  }

  /**
   * @return The shared memory for the state machine.
   */
  @Override
  public SharedMemory getSharedMemory() {
    return this.sharedMemory;
  }

  /**
   * Sets the state machine parameters, clearing out any existing parameters.
   *
   * Parameters are:
   * (boolean) always pass verification tests, e.g. false (default).
   * (int) the number of times that a ticket should be validated to provoke double spend, e.g. 2 (default).
   * (int) number of r bits to use in Type A elliptic curve, e.g. 256 (default).
   * (int) number of q bits to use in Type A elliptic curve, e.g. 512 (default).
   *
   * @param parameters The list of parameters.
   */
  @Override
  public void setParameters(List<String> parameters) {
    super.setParameters(parameters);

    // Pull out the relevant parameters into the shared memory.
    try {
      if (parameters.size() > 0) {
        this.sharedMemory.skipVerification = Boolean.parseBoolean(parameters.get(0));
      }

      if (parameters.size() > 1) {
        this.sharedMemory.numValidations = Integer.parseInt(parameters.get(1));
      }

      if (parameters.size()>2) {
    	  String pairingType=parameters.get(2);
    	  switch (pairingType) {
		case "A":
			this.sharedMemory.setPairingType(PairingType.TYPE_A);
			break;
		case "A1":
			this.sharedMemory.setPairingType(PairingType.TYPE_A1);
			//this pairing uses slightly different parameters
			//the number of primes to use
			this.sharedMemory.rBits=3;
			//the size of these primes
			this.sharedMemory.qBits=160;
			break;
		case "E":
			this.sharedMemory.setPairingType(PairingType.TYPE_E);
			break;
		default:
			throw new UnsupportedOperationException("This pairing type is not supported: "+pairingType);
		}
      }

      if (parameters.size() > 3) {
    	  //note for type A1 pairing this represents the number of primes to use
        this.sharedMemory.rBits = Integer.parseInt(parameters.get(3));
      }

      if (parameters.size() > 4) {
    	  //for type A1 pairings this represents the size of the primes
        this.sharedMemory.qBits = Integer.parseInt(parameters.get(4));
      }
      LOG.debug("ignore verfication failures:" + (this.sharedMemory.skipVerification));
      LOG.debug("bilinear group parameters (" + this.sharedMemory.rBits + ", " + this.sharedMemory.qBits + ")");
    }
    catch (final Exception e) {
      LOG.error("could not set parameters", e);
    }
  }

  /**
   * Sets the shared memory for the state machine.
   *
   * @param sharedMemory The shared memory to set.
   */
  @Override
  public void setSharedMemory(SharedMemory sharedMemory) {
    this.sharedMemory = (PPETSABCSharedMemory) sharedMemory;
  }
}
