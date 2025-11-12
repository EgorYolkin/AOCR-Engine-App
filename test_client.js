#!/usr/bin/env node
/**
 * Тестовый клиент для проверки OCR сервера (JavaScript/Node.js)
 * 
 * Установка зависимостей:
 *   npm install ws axios form-data
 * 
 * Использование:
 *   node test_client.js --host 192.168.1.10 --port 8080 --image test.jpg --mode status
 */

const fs = require('fs');
const axios = require('axios');
const WebSocket = require('ws');
const FormData = require('form-data');

function parseArgs() {
    const args = {
        host: '192.168.1.10',
        port: 8080,
        image: null,
        language: null,
        mode: 'status'
    };
    
    for (let i = 2; i < process.argv.length; i++) {
        if (process.argv[i] === '--host' && process.argv[i + 1]) {
            args.host = process.argv[++i];
        } else if (process.argv[i] === '--port' && process.argv[i + 1]) {
            args.port = parseInt(process.argv[++i]);
        } else if (process.argv[i] === '--image' && process.argv[i + 1]) {
            args.image = process.argv[++i];
        } else if (process.argv[i] === '--language' && process.argv[i + 1]) {
            args.language = process.argv[++i];
        } else if (process.argv[i] === '--mode' && process.argv[i + 1]) {
            args.mode = process.argv[++i];
        }
    }
    
    return args;
}

async function testHttpApi(host, port, imagePath, language = null) {
    const url = `http://${host}:${port}/ocr`;
    
    console.log(`Тестирование HTTP API: ${url}`);
    if (language) {
        console.log(`Язык: ${language}`);
    }
    
    try {
        const form = new FormData();
        form.append('image', fs.createReadStream(imagePath));
        if (language) {
            form.append('language', language);
        }
        
        const response = await axios.post(url, form, {
            headers: form.getHeaders()
        });
        
        console.log(`Статус код: ${response.status}`);
        console.log('Успешно! Распознанный текст:');
        console.log(`  Текст: ${response.data.text || 'N/A'}`);
        console.log(`  Язык: ${response.data.language || 'N/A'}`);
        console.log(`  Уверенность: ${response.data.confidence || 'N/A'}`);
        console.log(`  Время обработки: ${response.data.processingTimeMs || 'N/A'}ms`);
    } catch (error) {
        console.error(`Ошибка: ${error.message}`);
        if (error.response) {
            console.error(`Статус: ${error.response.status}`);
            console.error(`Данные: ${JSON.stringify(error.response.data)}`);
        }
    }
}

function testWebSocket(host, port, imagePath, language = null) {
    const wsUrl = `ws://${host}:${port}/ws`;
    
    console.log(`\nТестирование WebSocket API: ${wsUrl}`);
    if (language) {
        console.log(`Язык: ${language}`);
    }
    
    const imageData = fs.readFileSync(imagePath, { encoding: 'base64' });
    const ws = new WebSocket(wsUrl);
    
    ws.on('open', () => {
        console.log('WebSocket соединение установлено');
        
        const messageObj = {
            type: 'ocr',
            image: `data:image/jpeg;base64,${imageData}`
        };
        if (language) {
            messageObj.language = language;
        }
        const message = JSON.stringify(messageObj);
        
        ws.send(message);
    });
    
    ws.on('message', (data) => {
        const message = JSON.parse(data.toString());
        const msgType = message.type || 'unknown';
        
        process.stdout.write(`[${msgType}] `);
        
        switch (msgType) {
            case 'status':
                console.log(message.message || 'N/A');
                break;
            case 'progress':
                console.log(`Прогресс: ${message.progress || 0}%`);
                break;
            case 'result':
                console.log('Результат получен!');
                console.log(`  Текст: ${message.text || 'N/A'}`);
                console.log(`  Язык: ${message.language || 'N/A'}`);
                console.log(`  Уверенность: ${message.confidence || 'N/A'}`);
                console.log(`  Время обработки: ${message.processingTimeMs || 'N/A'}ms`);
                ws.close();
                break;
            case 'error':
                console.log(`Ошибка: ${message.error || 'N/A'}`);
                ws.close();
                break;
            default:
                console.log(JSON.stringify(message));
        }
    });
    
    ws.on('error', (error) => {
        console.error(`Ошибка WebSocket: ${error.message}`);
    });
    
    ws.on('close', () => {
        console.log('WebSocket соединение закрыто');
    });
}

async function testStatus(host, port) {
    const url = `http://${host}:${port}/status`;
    
    console.log(`\nПроверка статуса сервера: ${url}`);
    
    try {
        const response = await axios.get(url);
        
        console.log('Статус сервера:');
        console.log(`  Состояние: ${response.data.status || 'N/A'}`);
        console.log(`  Uptime: ${(response.data.uptime || 0) / 1000} секунд`);
        console.log(`  Количество запросов: ${response.data.requestCount || 0}`);
        console.log(`  Порт: ${response.data.port || 'N/A'}`);
        console.log(`  OCR движок: ${response.data.ocrEngine || 'N/A'}`);
    } catch (error) {
        console.error(`Не удалось подключиться к серверу: ${error.message}`);
    }
}

async function main() {
    const args = parseArgs();
    
    if (args.mode === 'status') {
        await testStatus(args.host, args.port);
    } else if (['http', 'ws', 'both'].includes(args.mode)) {
        if (!args.image) {
            console.error('Ошибка: необходимо указать путь к изображению (--image)');
            process.exit(1);
        }
        
        if (!fs.existsSync(args.image)) {
            console.error(`Ошибка: файл ${args.image} не найден`);
            process.exit(1);
        }
        
        if (args.mode === 'http' || args.mode === 'both') {
            await testHttpApi(args.host, args.port, args.image, args.language);
        }
        
        if (args.mode === 'ws' || args.mode === 'both') {
            testWebSocket(args.host, args.port, args.image, args.language);
        }
    } else {
        console.error('Неизвестный режим. Используйте: status, http, ws, или both');
        process.exit(1);
    }
}

main();

