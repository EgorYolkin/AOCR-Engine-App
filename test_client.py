#!/usr/bin/env python3
"""
Тестовый клиент для проверки OCR сервера
Использование:
    python test_client.py --host 192.168.1.10 --port 8080 --image test.jpg
"""

import argparse
import base64
import json
import requests
import websocket
import sys
from pathlib import Path


def test_http_api(host, port, image_path, language=None):
    """Тестирование HTTP API"""
    url = f"http://{host}:{port}/ocr"
    
    print(f"Тестирование HTTP API: {url}")
    if language:
        print(f"Язык: {language}")
    
    with open(image_path, 'rb') as f:
        files = {'image': f}
        data = {'language': language} if language else {}
        response = requests.post(url, files=files, data=data)
    
    print(f"Статус код: {response.status_code}")
    
    if response.status_code == 200:
        result = response.json()
        print(f"Успешно! Распознанный текст:")
        print(f"  Текст: {result.get('text', 'N/A')}")
        print(f"  Язык: {result.get('language', 'N/A')}")
        print(f"  Уверенность: {result.get('confidence', 'N/A')}")
        print(f"  Время обработки: {result.get('processingTimeMs', 'N/A')}ms")
    else:
        print(f"Ошибка: {response.text}")


def test_websocket(host, port, image_path, language=None):
    """Тестирование WebSocket API"""
    ws_url = f"ws://{host}:{port}/ws"
    
    print(f"\nТестирование WebSocket API: {ws_url}")
    if language:
        print(f"Язык: {language}")
    
    with open(image_path, 'rb') as f:
        image_data = base64.b64encode(f.read()).decode('utf-8')
    
    def on_message(ws, message):
        data = json.loads(message)
        msg_type = data.get('type', 'unknown')
        
        print(f"[{msg_type}] ", end='')
        
        if msg_type == 'status':
            print(f"{data.get('message', 'N/A')}")
        elif msg_type == 'progress':
            print(f"Прогресс: {data.get('progress', 0)}%")
        elif msg_type == 'result':
            print(f"Результат получен!")
            print(f"  Текст: {data.get('text', 'N/A')}")
            print(f"  Язык: {data.get('language', 'N/A')}")
            print(f"  Уверенность: {data.get('confidence', 'N/A')}")
            print(f"  Время обработки: {data.get('processingTimeMs', 'N/A')}ms")
            ws.close()
        elif msg_type == 'error':
            print(f"Ошибка: {data.get('error', 'N/A')}")
            ws.close()
        else:
            print(json.dumps(data, ensure_ascii=False))
    
    def on_error(ws, error):
        print(f"Ошибка WebSocket: {error}")
    
    def on_close(ws, close_status_code, close_msg):
        print("WebSocket соединение закрыто")
    
    def on_open(ws):
        print("WebSocket соединение установлено")
        message = {
            'type': 'ocr',
            'image': f'data:image/jpeg;base64,{image_data}'
        }
        if language:
            message['language'] = language
        ws.send(json.dumps(message))
    
    ws = websocket.WebSocketApp(
        ws_url,
        on_open=on_open,
        on_message=on_message,
        on_error=on_error,
        on_close=on_close
    )
    
    ws.run_forever()


def test_status(host, port):
    """Проверка статуса сервера"""
    url = f"http://{host}:{port}/status"
    
    print(f"\nПроверка статуса сервера: {url}")
    
    try:
        response = requests.get(url)
        if response.status_code == 200:
            result = response.json()
            print("Статус сервера:")
            print(f"  Состояние: {result.get('status', 'N/A')}")
            print(f"  Uptime: {result.get('uptime', 0) / 1000:.1f} секунд")
            print(f"  Количество запросов: {result.get('requestCount', 0)}")
            print(f"  Порт: {result.get('port', 'N/A')}")
            print(f"  OCR движок: {result.get('ocrEngine', 'N/A')}")
        else:
            print(f"Ошибка: {response.status_code} - {response.text}")
    except Exception as e:
        print(f"Не удалось подключиться к серверу: {e}")


def main():
    parser = argparse.ArgumentParser(
        description='Тестовый клиент для OCR сервера'
    )
    parser.add_argument(
        '--host',
        default='192.168.1.10',
        help='IP адрес сервера (по умолчанию: 192.168.1.10)'
    )
    parser.add_argument(
        '--port',
        type=int,
        default=8080,
        help='Порт сервера (по умолчанию: 8080)'
    )
    parser.add_argument(
        '--image',
        help='Путь к изображению для распознавания'
    )
    parser.add_argument(
        '--language',
        help='Язык для OCR (eng - английский, rus - русский, auto - автоопределение)'
    )
    parser.add_argument(
        '--mode',
        choices=['http', 'ws', 'both', 'status'],
        default='status',
        help='Режим тестирования (по умолчанию: status)'
    )
    
    args = parser.parse_args()
    
    if args.mode == 'status':
        test_status(args.host, args.port)
    elif args.mode in ['http', 'ws', 'both']:
        if not args.image:
            print("Ошибка: необходимо указать путь к изображению (--image)")
            sys.exit(1)
        
        if not Path(args.image).exists():
            print(f"Ошибка: файл {args.image} не найден")
            sys.exit(1)
        
        if args.mode in ['http', 'both']:
            test_http_api(args.host, args.port, args.image, args.language)
        
        if args.mode in ['ws', 'both']:
            test_websocket(args.host, args.port, args.image, args.language)


if __name__ == '__main__':
    main()

