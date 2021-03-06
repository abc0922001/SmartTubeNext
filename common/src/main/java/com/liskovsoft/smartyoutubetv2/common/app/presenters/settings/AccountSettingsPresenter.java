package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.SignInManager;
import com.liskovsoft.mediaserviceinterfaces.data.Account;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.RxUtils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class AccountSettingsPresenter {
    @SuppressLint("StaticFieldLeak")
    private static AccountSettingsPresenter sInstance;
    private final Context mContext;
    private final SignInManager mSignInManager;
    private Disposable mAccountsAction;
    private List<Account> mPendingRemove = new ArrayList<>();
    private Account mSelectedAccount = null;

    public AccountSettingsPresenter(Context context) {
        mContext = context;
        MediaService service = YouTubeMediaService.instance();
        mSignInManager = service.getSignInManager();
    }

    public static AccountSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountSettingsPresenter(context.getApplicationContext());
        }

        return sInstance;
    }

    public void unhold() {
        RxUtils.disposeActions(mAccountsAction);
        mPendingRemove.clear();
        mSelectedAccount = null;
        sInstance = null;
    }

    public void show() {
        mAccountsAction = mSignInManager.getAccountsObserve()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::createAndShowDialog);
    }

    private void createAndShowDialog(List<Account> accounts) {
        AppSettingsPresenter settingsPresenter = AppSettingsPresenter.instance(mContext);
        settingsPresenter.clear();

        createSelectAccountSection(accounts, settingsPresenter);
        createRemoveAccountSection(accounts, settingsPresenter);
        createAddAccountButton(settingsPresenter);

        settingsPresenter.showDialog(mContext.getString(R.string.settings_accounts), () -> {
            for (Account account : mPendingRemove) {
                mSignInManager.removeAccount(account);
            }

            mSignInManager.selectAccount(mSelectedAccount);

            unhold();
        });
    }

    private void createSelectAccountSection(List<Account> accounts, AppSettingsPresenter settingsPresenter) {
        List<OptionItem> optionItems = new ArrayList<>();

        optionItems.add(UiOptionItem.from(
                mContext.getString(R.string.dialog_account_none), optionItem -> mSelectedAccount = null, true
        ));

        for (Account account : accounts) {
            if (account.isSelected()) {
                mSelectedAccount = account;
            }

            optionItems.add(UiOptionItem.from(
                    formatAccount(account), option -> mSelectedAccount = account, account.isSelected()
            ));
        }

        settingsPresenter.appendRadioCategory(mContext.getString(R.string.dialog_account_list), optionItems);
    }

    private void createRemoveAccountSection(List<Account> accounts, AppSettingsPresenter settingsPresenter) {
        List<OptionItem> optionItems = new ArrayList<>();

        for (Account account : accounts) {
            optionItems.add(UiOptionItem.from(
                    formatAccount(account), option -> {
                        if (option.isSelected()) {
                            mPendingRemove.add(account);
                        } else {
                            mPendingRemove.remove(account);
                        }
                    }, false
            ));
        }

        settingsPresenter.appendCheckedCategory(mContext.getString(R.string.dialog_remove_account), optionItems);
    }

    private void createAddAccountButton(AppSettingsPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                mContext.getString(R.string.dialog_add_account), option -> SignInPresenter.instance(mContext).start()));
    }

    private String formatAccount(Account account) {
        String format;

        if (account.getEmail() != null) {
            format = String.format("%s (%s)", account.getName(), account.getEmail());
        } else {
            format = account.getName();
        }

        return format;
    }
}
