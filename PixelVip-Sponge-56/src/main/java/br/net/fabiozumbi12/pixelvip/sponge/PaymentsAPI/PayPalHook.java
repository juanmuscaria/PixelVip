package br.net.fabiozumbi12.pixelvip.sponge.PaymentsAPI;

import br.net.fabiozumbi12.pixelvip.sponge.PixelVip;
import org.spongepowered.api.entity.living.player.Player;
import urn.ebay.api.PayPalAPI.GetTransactionDetailsReq;
import urn.ebay.api.PayPalAPI.GetTransactionDetailsRequestType;
import urn.ebay.api.PayPalAPI.GetTransactionDetailsResponseType;
import urn.ebay.api.PayPalAPI.PayPalAPIInterfaceServiceService;
import urn.ebay.apis.eBLBaseComponents.PaymentItemType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class PayPalHook implements PaymentModel {
    private PixelVip plugin;
    private boolean sandbox;
    private PayPalAPIInterfaceServiceService payPalAPIInterfaceServiceService;

    public PayPalHook(PixelVip plugin) {
        this.plugin = plugin;
        this.sandbox = plugin.getConfig().root().apis.paypal.sandbox;
        try {
            Map<String, String> customProperties = new HashMap<String, String>() {{
                put("mode", sandbox ? "sandbox" : "live");
                put("acct1.UserName", plugin.getConfig().root().apis.paypal.username);
                put("acct1.Password", plugin.getConfig().root().apis.paypal.password);
                put("acct1.Signature", plugin.getConfig().root().apis.paypal.signature);
            }};

            payPalAPIInterfaceServiceService = new PayPalAPIInterfaceServiceService(customProperties);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public String getPayname() {
        return "PayPal";
    }

    @Override
    public boolean checkTransaction(Player player, String transCode) {

        if (plugin.getConfig().transExist(getPayname(), transCode)) {
            player.sendMessage(plugin.getUtil().toText(plugin.getConfig().root().strings._pluginTag + plugin.getConfig().root().strings.pay_codeused.replace("{payment}", getPayname())));
            plugin.processTrans.remove(transCode);
            return true;
        }

        boolean success;
        try {
            GetTransactionDetailsReq getTransactionDetailsReq = new GetTransactionDetailsReq();
            GetTransactionDetailsRequestType requestType = new GetTransactionDetailsRequestType();
            requestType.setTransactionID(transCode);
            requestType.setDetailLevel(null);
            getTransactionDetailsReq.setGetTransactionDetailsRequest(requestType);
            GetTransactionDetailsResponseType trans = payPalAPIInterfaceServiceService.getTransactionDetails(getTransactionDetailsReq);

            if (!trans.getErrors().isEmpty()) {
                return false;
            }

            if (!trans.getPaymentTransactionDetails().getPaymentInfo().getPaymentStatus().getValue().equalsIgnoreCase("Completed")) {
                player.sendMessage(plugin.getUtil().toText(plugin.getConfig().root().strings._pluginTag + plugin.getConfig().root().strings.pay_waiting.replace("{payment}", getPayname())));
                plugin.processTrans.remove(transCode);
                return true;
            }

            Date oldCf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse(plugin.getConfig().root().apis.paypal.ignoreOldest);
            Date payCf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(trans.getPaymentTransactionDetails().getPaymentInfo().getPaymentDate());
            if (payCf.compareTo(oldCf) < 0) {
                player.sendMessage(plugin.getUtil().toText(plugin.getConfig().root().strings._pluginTag + plugin.getConfig().root().strings.pay_expired.replace("{payment}", getPayname())));
                return true;
            }

            HashMap<Integer, String> items = new HashMap<>();
            for (PaymentItemType pay : trans.getPaymentTransactionDetails().getPaymentItemInfo().getPaymentItem()) {
                String[] ids = pay.getName().split(" ");
                for (String id : ids)
                    if (id.startsWith("#"))
                        items.put(Integer.parseInt(pay.getQuantity()), id.substring(1));
            }

            success = plugin.getUtil().paymentItems(items, player, this.getPayname(), transCode);
        } catch (Exception e) {
            e.printStackTrace();
            plugin.processTrans.remove(transCode);
            return false;
        }

        //if success
        if (success && !sandbox) plugin.getConfig().addTrans(this.getPayname(), transCode, player.getName());
        plugin.processTrans.remove(transCode);
        return true;
    }
}
