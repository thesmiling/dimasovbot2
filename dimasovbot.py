import telebot

TOKEN = '406374437:AAF96C4YUsV8_-SLsNTKDkSsp9AzxWS9o1c' 
bot = telebot.TeleBot(TOKEN)

@bot.message_handler(content_types=["text"])
def repeat_all_messages(message): # Название функции не играет никакой роли, в принципе
    bot.send_message(message.chat.id, message.text)

if __name__ == '__main__':
     bot.polling(none_stop=True)
