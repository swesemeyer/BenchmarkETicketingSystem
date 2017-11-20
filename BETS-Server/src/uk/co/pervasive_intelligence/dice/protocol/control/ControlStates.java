/**
 * DICE NFC evaluation.
 *
 * (c) University of Surrey and Pervasive Intelligence Ltd 2017.
 */
package uk.co.pervasive_intelligence.dice.protocol.control;

import uk.co.pervasive_intelligence.dice.protocol.NFCReaderCommand;
import uk.co.pervasive_intelligence.dice.protocol.NFCSharedMemory;
import uk.co.pervasive_intelligence.dice.state.Action;
import uk.co.pervasive_intelligence.dice.state.Action.Status;
import uk.co.pervasive_intelligence.dice.state.Message;
import uk.co.pervasive_intelligence.dice.state.Message.Type;
import uk.co.pervasive_intelligence.dice.state.State;

/**
 * States of the control state machine protocol.
 *
 * @author Matthew Casey
 */
public class ControlStates {

  /**
   * State 0.
   */
  public static class ControlState0 extends State<NFCReaderCommand> {

    /**
     * Gets the required action given a message.
     *
     * @param message The received message to process.
     * @return The required action.
     */
    @Override
    public Action<NFCReaderCommand> getAction(Message message) {
      if (message.getType() == Type.START) {
        // Open the connection.
        return new Action<>(1, NFCReaderCommand.OPEN);
      }
      else if (message.getType() == Type.SUCCESS) {
        // Successful completion.
        return new Action<>(Status.END_SUCCESS);
      }

      return super.getAction(message);
    }
  }

  /**
   * State 1.
   */
  public static class ControlState1 extends State<NFCReaderCommand> {

    /**
     * Gets the required action given a message.
     *
     * @param message The received message to process.
     * @return The required action.
     */
    @Override
    public Action<NFCReaderCommand> getAction(Message message) {
      if (message.getType() == Type.SUCCESS) {
        // Select the AID for control. We do this twice because it sometimes doesn't work.
        return new Action<>(Status.CONTINUE, 2, NFCReaderCommand.SELECT, NFCSharedMemory.AID, 0);
      }

      return super.getAction(message);
    }
  }
}
