package me.brendanweinstein.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.paymentkit.R;

import me.brendanweinstein.CardType;
import me.brendanweinstein.ValidateCreditCard;
import me.brendanweinstein.util.ViewUtils;

/**
 * 
 * @author Brendan Weinstein
 *
 */
public class FieldHolder extends RelativeLayout {

	private static final String TAG = FieldHolder.class.getSimpleName();
	
	protected static final int AMEX_CARD_LENGTH = 17;
	public static final int NON_AMEX_CARD_LENGTH = 19;
	
	private static final int RE_ENTRY_ALPHA_OUT_DURATION = 100;
	private static final int RE_ENTRY_ALPHA_IN_DURATION = 500;
	private static final int RE_ENTRY_OVERSHOOT_DURATION = 500;
	
	private CardNumHolder mCardHolder;
	private ExpirationEditText mExpirationEditText;
	private CVVEditText mCVVEditText;
	private CardIcon mCardIcon;
	private LinearLayout mExtraFields;
	private CompletionListener mCompletionListener;

	private int mOriginalTextColor = Color.BLACK;

	public interface CompletionListener {
		void onValidFormComplete();
		void onFormInvalidated();
	}
	
	public FieldHolder(Context context) {
		super(context);
		setup();
	}

	public FieldHolder(Context context, AttributeSet attrs) {
		super(context, attrs);
		setup();
	}
	
	public CVVEditText getCVVEditText() {
		return mCVVEditText;
	}
	
	public CardIcon getCardIcon() {
		return mCardIcon;
	}
	
	public ExpirationEditText getExpirationEditText() {
		return mExpirationEditText;
	}
	
	public CardNumHolder getCardNumHolder() {
		return mCardHolder;
	}
	
	public void lockCardNumField() {
		transitionToExtraFields();
	}
	
	private void setup() {
		LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.pk_field_holder, this, true);
		mCardHolder = (CardNumHolder) findViewById(R.id.card_num_holder);
		mCardIcon = (CardIcon) findViewById(R.id.card_icon);
		mExtraFields = (LinearLayout) findViewById(R.id.extra_fields);
		mExpirationEditText = (ExpirationEditText) findViewById(R.id.expiration);
		mCVVEditText = (CVVEditText) findViewById(R.id.security_code);
		mCardHolder.setCardEntryListener(mCardEntryListener);
		setupViews();
	}
	
	private void setupViews() {
		setExtraFieldsAlpha();
		setCardEntryListeners();
		setNecessaryFields();
	}
	
	private void setNecessaryFields() {
		setFocusable(true);
		setFocusableInTouchMode(true);
		setClipChildren(false);
	}
	
	private void setExtraFieldsAlpha() {
		ObjectAnimator setAlphaZero = ObjectAnimator.ofFloat(mExtraFields, "alpha", 0.0f);
		setAlphaZero.setDuration(0);
		setAlphaZero.start();
		mExtraFields.setVisibility(View.GONE);
	}

	private void setCardEntryListeners() {
		mExpirationEditText.setCardEntryListener(mCardEntryListener);
		mCVVEditText.setCardEntryListener(mCardEntryListener);
		mCardHolder.getCardField().addTextChangedListener(new PaymentFormCompletionTextWatcher() {
			@Override
			public void afterTextChanged(Editable editable) {
                super.afterTextChanged(editable);

                // Only reset the color if we're showing the invalid card number color (see CardNumHolder.indicateInvalidCardNum())
				if (isUsingInvalidCardNumberColor()) {
					CardType cardType = ValidateCreditCard.getCardType(mCardHolder.getCardField().getText().toString());
					switch (cardType) {
						case AMERICAN_EXPRESS:
							if (editable.length() < AMEX_CARD_LENGTH) {
								mCardHolder.getCardField().setTextColor(mOriginalTextColor);
							}
							break;
						default:
							if (editable.length() < NON_AMEX_CARD_LENGTH) {
								mCardHolder.getCardField().setTextColor(mOriginalTextColor);
							}
							break;
					}
				}
			}
		});
        mExpirationEditText.addTextChangedListener(new PaymentFormCompletionTextWatcher());
        mCVVEditText.addTextChangedListener(new PaymentFormCompletionTextWatcher());
	}

    private boolean isUsingInvalidCardNumberColor() {
        return mCardHolder.getCardField().getCurrentTextColor() == Color.RED;
    }

	private void validateCard() {
		long cardNumber = Long.parseLong(mCardHolder.getCardField().getText().toString().replaceAll("\\s", ""));
		if (ValidateCreditCard.isValid(cardNumber)) {
			CardType cardType = ValidateCreditCard.matchCardType(cardNumber);
			mCardIcon.setCardType(cardType);
			transitionToExtraFields();
		} else {
			mCardHolder.indicateInvalidCardNum();
		}
	}

	private void transitionToExtraFields() {
		// CREATE LAST 4 DIGITS OVERLAY
		mCardHolder.createOverlay();

		// MOVE CARD NUMBER TO LEFT AND ALPHA OUT
		AnimatorSet set = new AnimatorSet();
		ViewUtils.setHardwareLayer(mCardHolder);
		ObjectAnimator translateAnim = ObjectAnimator.ofFloat(mCardHolder, "translationX", -mCardHolder.getLeftOffset());
		translateAnim.setDuration(500);

		ObjectAnimator alphaOut = ObjectAnimator.ofFloat(mCardHolder.getCardField(), "alpha", 0.0f);
		alphaOut.setDuration(500);
		alphaOut.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator anim) {
				mCardHolder.getCardField().setVisibility(View.GONE);
				ViewUtils.setLayerTypeNone(mCardHolder);
			}
		});

		// ALPHA IN OTHER FIELDS
		mExtraFields.setVisibility(View.VISIBLE);
		ObjectAnimator alphaIn = ObjectAnimator.ofFloat(mExtraFields, "alpha", 1.0f);
		alphaIn.setDuration(500);
		set.playTogether(translateAnim, alphaOut, alphaIn);
		set.start();

		mExpirationEditText.requestFocus();
	}
	
	public interface CardEntryListener {
		public void onCardNumberInputComplete();

		public void onEdit();

		public void onCardNumberInputReEntry();

		public void onCVVEntry();

		public void onCVVEntryComplete();

		public void onBackFromCVV();

	}
	
	CardEntryListener mCardEntryListener = new CardEntryListener() {
		@Override
		public void onCardNumberInputComplete() {
			validateCard();
		}

		@Override
		public void onEdit() {
			CardType newCardType = ValidateCreditCard.getCardType(mCardHolder.getCardField().getText().toString());
			if (newCardType == CardType.AMERICAN_EXPRESS) {
				mCardHolder.getCardField().setMaxCardLength(AMEX_CARD_LENGTH);
				mCVVEditText.setCvvMaxLength(4);
			} else {
				mCardHolder.getCardField().setMaxCardLength(NON_AMEX_CARD_LENGTH);
				mCVVEditText.setCvvMaxLength(3);
			}
			mCardIcon.setCardType(ValidateCreditCard.getCardType(mCardHolder.getCardField().getText().toString()));
		}

		@Override
		public void onCardNumberInputReEntry() {
			mCardIcon.flipTo(CardIcon.CardFace.FRONT);
			AnimatorSet set = new AnimatorSet();

			mCardHolder.getCardField().setVisibility(View.VISIBLE);
			ObjectAnimator alphaOut = ObjectAnimator.ofFloat(mExtraFields, "alpha", 0.0f);
			alphaOut.setDuration(RE_ENTRY_ALPHA_OUT_DURATION);
			alphaOut.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator anim) {
					mExtraFields.setVisibility(View.GONE);
					mCardHolder.destroyOverlay();
					mCardHolder.getCardField().requestFocus();
					mCardHolder.getCardField().setSelection(mCardHolder.getCardField().length());
				}
			});

			ObjectAnimator alphaIn = ObjectAnimator.ofFloat(mCardHolder.getCardField(), "alpha", 0.5f, 1.0f);
			alphaIn.setDuration(RE_ENTRY_ALPHA_IN_DURATION);

			ObjectAnimator overShoot = ObjectAnimator.ofFloat(mCardHolder, "translationX", -mCardHolder.getLeftOffset(), 0.0f);
			overShoot.setInterpolator(new OvershootInterpolator());
			overShoot.setDuration(RE_ENTRY_OVERSHOOT_DURATION);

			set.playTogether(alphaOut, alphaIn, overShoot);
			set.start();
		}

		@Override
		public void onCVVEntry() {
			mCardIcon.flipTo(CardIcon.CardFace.BACK);
			mCVVEditText.requestFocus();
		}

		@Override
		public void onCVVEntryComplete() {
			Log.d(TAG, "onCVVEntryComplete");
			if (isFieldsValid()) {
				mCardIcon.flipTo(CardIcon.CardFace.FRONT);
				if (mCompletionListener != null) {
					mCompletionListener.onValidFormComplete();
				}
			}
		}

		@Override
		public void onBackFromCVV() {
			Log.d(TAG, "onBackFromCVV");
			mExpirationEditText.requestFocus();
			mCardIcon.flipTo(CardIcon.CardFace.FRONT);
		}

	};
	
	public boolean isFieldsValid() {
		if (mExpirationEditText.getText().toString().length() != 5) {
			return false;
		} else if (mCVVEditText.getText().toString().length() != mCVVEditText.getCvvMaxLength()) {
			return false;
		}

        return mCardHolder.isCardNumValid();
	}

	public boolean isValidCard() {
		long cardNumber = Long.parseLong(mCardHolder.getCardField().getText().toString().replaceAll("\\s", ""));
		return ValidateCreditCard.isValid(cardNumber);
	}

	public void setCompletionListener(CompletionListener completionListener) {
		mCompletionListener = completionListener;
	}

    private class PaymentFormCompletionTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (mCompletionListener != null) {
                if (isFieldsValid()) {
                    mCompletionListener.onValidFormComplete();
                } else {
                    mCompletionListener.onFormInvalidated();
                }
            }
        }
    }
}
