package cn.edu.hut.crazyacking.spider.worker.impl;

import cn.edu.hut.crazyacking.spider.common.Utils;
import cn.edu.hut.crazyacking.spider.fetcher.WeiboFetcher;
import cn.edu.hut.crazyacking.spider.parser.bean.Account;
import cn.edu.hut.crazyacking.spider.queue.AccountQueue;
import cn.edu.hut.crazyacking.spider.queue.VisitedWeiboUrlQueue;
import cn.edu.hut.crazyacking.spider.queue.WeiboUrlQueue;
import cn.edu.hut.crazyacking.spider.worker.BasicWorker;
import cn.edu.hut.crazyacking.spider.parser.WeiboParser;
import cn.edu.hut.crazyacking.spider.queue.AbnormalUrlQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * 从UrlQueue中取出url，下载页面，分析url，保存已访问rul
 *
 * @author crazyacking
 */
public class AbnormalUrlWorker extends BasicWorker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(AbnormalUrlWorker.class.getName());

    /**
     * 下载对应页面并分析出页面对应URL，放置在未访问队列中
     *
     * @param url 返回值：被封账号/系统繁忙/OK
     */
    protected String dataHandler(String url) {
        logger.info("-------------------");
        logger.info("抓取到：" + WeiboUrlQueue.size());
        logger.info("已处理：" + VisitedWeiboUrlQueue.size());
        logger.info("异常数：" + AbnormalUrlQueue.size());
        logger.info("-------------------");

        return WeiboFetcher.getContentFromUrl(url).getContent();
    }

    @Override
    public void run() {
        // 首先获取账号并登录
        Account account = AccountQueue.outElement();
        AccountQueue.addElement(account);
        this.username = account.getUserName();
        this.password = account.getPassword();

        // 使用账号登录
        String gsid = login(username, password);
        String result;
        try {
            // 若登录失败，则执行一轮切换账户的操作，如果还失败，则退出
            if (gsid == null) {
                gsid = switchAccount();
            }

            // 登录成功
            if (gsid != null) {
                // 当URL队列不为空时，从未访问队列中取出url进行分析
                while (!WeiboUrlQueue.isEmpty()) {
                    // 从队列中获取URL并处理
                    result = dataHandler(WeiboUrlQueue.outElement() + "&" + gsid);

                    // 针对处理结果进行处理：OK, SYSTEM_BUSY, ACCOUNT_FORBIDDEN
                    gsid = process(result, gsid);

                    // 没有新的URL了，从数据库中继续拿一个
                    if (WeiboUrlQueue.isEmpty()) {
                        logger.info(">> All abnormal weibos have been fetched...");
                        break;
                    }
                }
            } else {
                logger.info(username + " login failed!");
            }

        } catch (Exception e) {
            logger.error("", e);
        }

        // 关闭数据库连接
        try {
            WeiboParser.conn.close();
            Utils.conn.close();
        } catch (SQLException e) {
            logger.error("", e);
        }

        logger.info("Spider stop...");
    }

}
