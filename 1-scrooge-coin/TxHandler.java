import java.util.ArrayList;
import java.util.Arrays;

public class TxHandler {
    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        ArrayList<UTXO> claimed = new ArrayList<>();
        double inputSum = 0;
        int i = 0;
        for (Transaction.Input input : tx.getInputs()) {
            UTXO ut = new UTXO(input.prevTxHash, input.outputIndex);
            Transaction.Output output = utxoPool.getTxOutput(ut);
            if (output == null) {
                return false;
            }
            if (claimed.contains(ut)) {
                return false;
            }
            claimed.add(ut);
            if (!Crypto.verifySignature(output.address,
                    tx.getRawDataToSign(i),
                    input.signature)) {
                return false;
            }
            inputSum += output.value;
            i += 1;
        }
        double outputSum = 0;
        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) {
                return false;
            }
            outputSum += output.value;
        }
        if (inputSum < outputSum) {
            return false;
        }

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> accepted = new ArrayList<>();
        ArrayList<Transaction> processed = new ArrayList<>(Arrays.asList(possibleTxs));
        Transaction tx;
        do {
            tx = null;
            for (Transaction t : processed) {
                if (isValidTx(t)) {
                    tx = t;
                    break;
                }
            }
            if (tx != null) {
                handleTx(tx);
                accepted.add(tx);
                processed.remove(tx);
            }
        } while (tx != null);
        Transaction[] result = new Transaction[accepted.size()];
        result = accepted.toArray(result);
        return result;
    }

    private void handleTx(Transaction tx) {
        for (Transaction.Input input : tx.getInputs()) {
            UTXO ut = new UTXO(input.prevTxHash, input.outputIndex);
            utxoPool.removeUTXO(ut);
        }
        for (int i = 0; i < tx.numOutputs(); i++) {
            UTXO ut = new UTXO(tx.getHash(), i);
            utxoPool.addUTXO(ut, tx.getOutput(i));
        }
    }

}
