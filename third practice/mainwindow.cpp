#include "mainwindow.h"
#include "ui_mainwindow.h"

#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QJsonObject>
#include <QJsonDocument>

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent)
    , ui(new Ui::MainWindow)
{
    ui->setupUi(this);
}

MainWindow::~MainWindow()
{
    delete ui;
}

void sendEmail(QString to, QString subject, QString content,QString name)
{
    QNetworkAccessManager* mgr = new QNetworkAccessManager();

    QUrl url("http://localhost:8080/api/email/send");
    QNetworkRequest request(url);

    request.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");

    QJsonObject json;
    json["to"] = to;
    json["subject"] = subject;
    json["content"] = content;
    json["name"]=name;

    QNetworkReply* reply = mgr->post(request, QJsonDocument(json).toJson());

    QObject::connect(reply, &QNetworkReply::finished, [reply]() {
        if (reply->error() == QNetworkReply::NoError) {
            QString response = reply->readAll();
            qDebug() << "邮件发送成功：" << response;
        } else {
            qDebug() << "错误：" << reply->errorString();
        }
        reply->deleteLater();
    });
}

void MainWindow::on_pushButton_clicked()
{
    QString to = ui->lineEdit->text();
    QString subject = ui->lineEdit_2->text();
    QString content = ui->textEdit->toPlainText();
    QString name=ui->lineEdit_3->text();

    sendEmail(to, subject, content,name);
}
