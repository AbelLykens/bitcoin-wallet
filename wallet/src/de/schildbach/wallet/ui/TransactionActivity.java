/*
 * Copyright 2011-2013 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import android.content.Context;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Bundle;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.Nfc;
import de.schildbach.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class TransactionActivity extends AbstractWalletActivity
{
	public static final String INTENT_EXTRA_TRANSACTION_HASH = "transaction_hash";

	private NfcManager nfcManager;
	private Transaction tx;

	public static void show(final Context context, final Transaction tx)
	{
		final Intent intent = new Intent(context, TransactionActivity.class);
		intent.putExtra(TransactionActivity.INTENT_EXTRA_TRANSACTION_HASH, tx.getHash());
		context.startActivity(intent);
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		nfcManager = (NfcManager) getSystemService(Context.NFC_SERVICE);

		setContentView(R.layout.transaction_content);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		handleIntent(getIntent());
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		updateView();
	}

	@Override
	public void onPause()
	{
		Nfc.unpublish(nfcManager, this);

		super.onPause();
	}

	private void handleIntent(final Intent intent)
	{
		if (intent.hasExtra(INTENT_EXTRA_TRANSACTION_HASH))
		{
			final Wallet wallet = ((WalletApplication) getApplication()).getWallet();
			tx = wallet.getTransaction((Sha256Hash) intent.getSerializableExtra(INTENT_EXTRA_TRANSACTION_HASH));
		}
		else if (Constants.MIMETYPE_TRANSACTION.equals(intent.getType()))
		{
			final NdefMessage ndefMessage = (NdefMessage) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
			final byte[] payload = Nfc.extractMimePayload(Constants.MIMETYPE_TRANSACTION, ndefMessage);

			try
			{
				tx = new Transaction(Constants.NETWORK_PARAMETERS, payload);

				processPendingTransaction(tx);
			}
			catch (final ProtocolException x)
			{
				throw new RuntimeException(x);
			}
		}

		if (tx == null)
			throw new IllegalArgumentException("no tx");
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void updateView()
	{
		final TransactionFragment transactionFragment = (TransactionFragment) getSupportFragmentManager().findFragmentById(R.id.transaction_fragment);

		transactionFragment.update(tx);

		Nfc.publishMimeObject(nfcManager, this, Constants.MIMETYPE_TRANSACTION, tx.unsafeBitcoinSerialize(), false);
	}

	private void processPendingTransaction(final Transaction tx)
	{
		final Wallet wallet = ((WalletApplication) getApplication()).getWallet();

		try
		{
			if (wallet.isTransactionRelevant(tx))
				// TODO dependent transactions
				wallet.receivePending(tx, null);
		}
		catch (final VerificationException x)
		{
			throw new RuntimeException(x);
		}
	}
}
