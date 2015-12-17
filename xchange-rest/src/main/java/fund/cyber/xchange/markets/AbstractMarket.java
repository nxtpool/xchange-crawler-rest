package fund.cyber.xchange.markets;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.exceptions.NotAvailableFromExchangeException;
import com.xeiam.xchange.exceptions.NotYetImplementedForExchangeException;
import com.xeiam.xchange.service.BaseExchangeService;
import com.xeiam.xchange.service.polling.marketdata.PollingMarketDataService;
import fund.cyber.xchange.model.api.TickerDto;
import fund.cyber.xchange.service.ChaingearDataLoader;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract Market Service
 * <p>
 * @author Andrey Lobarev nxtpool@gmail.com
 */
public abstract class AbstractMarket<T extends BaseExchangeService> implements InitializingBean {

    protected Exchange exchange;

    protected PollingMarketDataService dataService;

    private Map<CurrencyPair, TickerDto> tickers;

    @Autowired
    private ChaingearDataLoader chaingearDataLoader;

    @Override
    public void afterPropertiesSet() throws Exception {
        initExchange();
        dataService = exchange.getPollingMarketDataService();
        tickers = new HashMap<>();
    }

    public abstract void initExchange();

    public List<CurrencyPair> getCurrencyPairs() throws IOException {
        return ((T) dataService).getExchangeSymbols();
    }

    public Ticker getTicker(CurrencyPair currencyPair) throws IOException {
        Ticker ticker = dataService.getTicker(currencyPair);
        if (ticker == null) {
            return null;
        }

        if (ticker.getTimestamp() != null) {
            return ticker;
        }

        List<Trade> trades;
        try {
            trades = dataService.getTrades(currencyPair).getTrades();
        } catch (NotAvailableFromExchangeException | NotYetImplementedForExchangeException e) {
            return ticker;
        }
        if (trades.size() == 0) {
            return ticker;
        }
        trades.sort((o1, o2) -> o2.getTimestamp().compareTo(o1.getTimestamp()));
        return (new Ticker.Builder()).currencyPair(currencyPair)
                .last(ticker.getLast())
                .bid(ticker.getBid())
                .ask(ticker.getAsk())
                .high(ticker.getHigh())
                .low(ticker.getLow())
                .vwap(ticker.getVwap())
                .volume(ticker.getVolume())
                .timestamp(trades.get(0).getTimestamp()).build();
    }

    public String getMarketUrl() {
        String url = exchange.getDefaultExchangeSpecification().getHost() != null ?
                exchange.getDefaultExchangeSpecification().getHost() :
                exchange.getDefaultExchangeSpecification().getSslUri();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        return url;
    }

    protected void loadData() throws IOException {
        List<TickerDto> tickers = new ArrayList<TickerDto>();
        for (CurrencyPair pair : getCurrencyPairs()) {
            if (!chaingearDataLoader.isCurrency(pair.counterSymbol) || !chaingearDataLoader.isCurrency(pair.baseSymbol)) {
                continue;
            }
            try {
                Ticker ticker = getTicker(pair);
                if (ticker != null) {
                    this.tickers.put(pair, chaingearDataLoader.createTickerDto(ticker, pair, getMarketUrl()));
                }
            } catch (IOException e) {
                System.out.print("Host: " + exchange.getDefaultExchangeSpecification().getHost() + ". Pair: " + pair.baseSymbol + "/" + pair.counterSymbol);
                System.out.println(e);
            }
        }
    }

    public Collection<TickerDto> getLastData() {
        return tickers.values();
    }

}