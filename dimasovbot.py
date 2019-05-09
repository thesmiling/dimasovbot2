import telebot
import bs4
import perser

TOKEN = "406374437:AAF96C4YUsV8_-SLsNTKDkSsp9AzxWS9o1c"
bot = telebot. TeleBot (TOKEN)

@bot.message_handler(commands=['start', 'go']) 
def start_handler(message): bot.send_message(message.chat.id, 'Привет, когда я вырасту, я буду парсить заголовки с Хабра') 


@bot.message_handler(content_types=['text']) 
def text_handler(message): text = message.text.lower() chat_id = message.chat.id 
if text == "привет": bot.send_message(chat_id, 'Привет, я бот - парсер хабра.') 
elif text == "как дела?": bot.send_message(chat_id, 'Хорошо, а у тебя?') 
else: bot.send_message(chat_id, 'Простите, я вам не понял :(')

bot.polling()
